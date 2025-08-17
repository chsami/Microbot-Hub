#!/usr/bin/env python3
import sys
import json
import subprocess

def get_json_from_git(ref, file_path):
    """Get JSON content from a specific git reference"""
    try:
        result = subprocess.run(['git', 'show', f'{ref}:{file_path}'],
                               capture_output=True, text=True, check=True)
        return json.loads(result.stdout)
    except subprocess.CalledProcessError:
        return None
    except json.JSONDecodeError:
        return []
    except Exception:
        return []

def main():
    current_plugins = []

    try:
        with open('public/docs/plugins.json', 'r') as f:
            current_plugins = json.load(f)
    except Exception as e:
        print(f"   (Unable to read current plugins.json: {e})")
        return

    old_plugins = get_json_from_git('HEAD~1', 'public/docs/plugins.json')

    if old_plugins is None:
        print("   (No previous plugins.json found for comparison)")
        for plugin in current_plugins:
            print(f"   +{plugin['internalName']}: {plugin['sha256'][:12]}")
        return

    if not old_plugins:
        print("   (Previous plugins.json was empty)")
        for plugin in current_plugins:
            print(f"   +{plugin['internalName']}: {plugin['sha256'][:12]}")
        return

    old_lookup = {p['internalName']: p['sha256'][:12] for p in old_plugins}
    current_lookup = {p['internalName']: p['sha256'][:12] for p in current_plugins}

    changes_found = False

    # Check for new and changed plugins
    for plugin_name, sha in current_lookup.items():
        if plugin_name not in old_lookup:
            print(f'   +{plugin_name}: {sha}')
            changes_found = True
        elif old_lookup[plugin_name] != sha:
            print(f'   {plugin_name}: {old_lookup[plugin_name]} -> {sha}')
            changes_found = True

    # Check for removed plugins
    for plugin_name in old_lookup:
        if plugin_name not in current_lookup:
            print(f'   -{plugin_name}: {old_lookup[plugin_name]}')
            changes_found = True

    if not changes_found:
        print("   (No plugin changes detected)")

if __name__ == "__main__":
    main()
