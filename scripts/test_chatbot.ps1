param(
    [Parameter(Mandatory = $true)]
    [string] $MicrobotClientPath,
    [string] $JdkPath = $env:JAVA_HOME,
    [string] $ClassesPath
)

$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $ClassesPath) {
    $ClassesPath = Join-Path $taskRoot 'build/classes/java/chatbot'
}
if (-not $JdkPath) { throw 'Pass -JdkPath with a JDK 11 directory.' }
$javac = Join-Path $JdkPath 'bin/javac.exe'
$java = Join-Path $JdkPath 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javac)) { throw "javac missing: $javac" }
if (-not (Test-Path -LiteralPath $MicrobotClientPath -PathType Leaf)) {
    throw "Microbot client JAR missing: $MicrobotClientPath"
}
if (-not (Test-Path -LiteralPath (Join-Path $ClassesPath 'net/runelite/client/plugins/microbot/chatbot/ChatbotPacing.class'))) {
    throw 'Compile the chatbot source set before running this harness.'
}

$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testOutput = [IO.Path]::GetFullPath((Join-Path $tempRoot ('chatbot-regression-' + [Guid]::NewGuid().ToString('N'))))
$source = Join-Path $taskRoot 'src/test/java/net/runelite/client/plugins/microbot/chatbot/ChatbotRegressionTest.java'
$compileClasspath = $ClassesPath + [IO.Path]::PathSeparator + $MicrobotClientPath
New-Item -ItemType Directory -Path $testOutput | Out-Null
try {
    & $javac '--release' '11' '-encoding' 'UTF-8' '-cp' $compileClasspath '-d' $testOutput $source
    if ($LASTEXITCODE -ne 0) { throw "Regression compilation failed with exit code $LASTEXITCODE" }
    $runClasspath = $testOutput + [IO.Path]::PathSeparator + $compileClasspath
    & $java '-ea' '-cp' $runClasspath 'net.runelite.client.plugins.microbot.chatbot.ChatbotRegressionTest'
    if ($LASTEXITCODE -ne 0) { throw "Chatbot regressions failed with exit code $LASTEXITCODE" }
}
finally {
    $verifiedOutput = [IO.Path]::GetFullPath($testOutput)
    $tempPrefix = $tempRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $verifiedOutput.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($verifiedOutput)).StartsWith('chatbot-regression-', [StringComparison]::Ordinal)) {
        throw "Refusing to remove unexpected test output path: $verifiedOutput"
    }
    Remove-Item -LiteralPath $verifiedOutput -Recurse -Force
}
