param(
    [Parameter(Mandatory = $true)]
    [string] $MicrobotClientPath,
    [string] $JdkPath = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$clientJar = (Resolve-Path -LiteralPath $MicrobotClientPath).Path
if (-not $JdkPath) { throw 'Pass -JdkPath with a JDK 11 directory.' }
$jdkDirectory = (Resolve-Path -LiteralPath $JdkPath).Path
if (-not (Test-Path -LiteralPath (Join-Path $jdkDirectory 'bin/javac.exe'))) {
    throw 'The selected directory must contain a JDK.'
}

# The shared Hub JAR task currently reads main output, which builds unrelated
# plugins. Package only the freshly compiled chatbot source set without changing
# shared build configuration or including stale classes from another source set.
$initSource = @'
gradle.projectsEvaluated {
    def project = gradle.rootProject
    def pluginFile = project.file('src/main/java/net/runelite/client/plugins/microbot/chatbot/ChatbotPlugin.java')
    def pluginVersion = project.ext.getPluginDescriptorInfo(pluginFile).version
    project.tasks.register('packageChatbotIsolated', org.gradle.api.tasks.bundling.Jar) {
        dependsOn project.tasks.named('compileChatbotJava')
        from(project.sourceSets.chatbot.output) {
            include 'net/runelite/client/plugins/microbot/chatbot/**'
            include 'net/runelite/client/plugins/microbot/PluginConstants.class'
        }
        archiveFileName.set("ChatbotPlugin-${pluginVersion}.jar")
        destinationDirectory.set(project.layout.buildDirectory.dir('libs'))
        preserveFileTimestamps = false
        reproducibleFileOrder = true
        manifest {
            attributes('Implementation-Title': 'ChatbotPlugin', 'Implementation-Version': pluginVersion)
        }
        eachFile { mode = 0644 }
    }
}
'@

$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$buildOutput = [IO.Path]::GetFullPath((Join-Path $tempRoot ('chatbot-build-' + [Guid]::NewGuid().ToString('N'))))
New-Item -ItemType Directory -Path $buildOutput | Out-Null
$initPath = Join-Path $buildOutput 'chatbot.gradle'
[IO.File]::WriteAllText($initPath, $initSource, (New-Object Text.UTF8Encoding($false)))
$previousJavaPath = $env:JAVA_HOME
Push-Location -LiteralPath $taskRoot
try {
    $env:JAVA_HOME = $jdkDirectory
    & (Join-Path $taskRoot 'gradlew.bat') '-I' $initPath 'packageChatbotIsolated' '-PpluginList=ChatbotPlugin' "-PmicrobotClientPath=$clientJar" "-Dorg.gradle.java.installations.paths=$jdkDirectory" '--offline' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Chatbot build failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaPath
    $verifiedOutput = [IO.Path]::GetFullPath($buildOutput)
    $tempPrefix = $tempRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $verifiedOutput.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($verifiedOutput)).StartsWith('chatbot-build-', [StringComparison]::Ordinal)) {
        throw "Refusing to remove unexpected build output path: $verifiedOutput"
    }
    Remove-Item -LiteralPath $verifiedOutput -Recurse -Force
}
