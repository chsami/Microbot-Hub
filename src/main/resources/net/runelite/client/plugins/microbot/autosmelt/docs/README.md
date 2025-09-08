# Auto Smelt Plugin

Automatically smelts bars at the Edgeville furnace with banking support.

## Features

- **Automatic Banking**: Walks to Edgeville bank when inventory is full or needs materials
- **Automatic Smelting**: Walks to Edgeville furnace and smelts the configured bar type
- **All Bar Types**: Supports Bronze, Iron, Silver, Steel, Gold, Mithril, Adamantite, and Runite bars
- **Smart Material Detection**: Automatically detects required ores and coal for each bar type
- **Logout Option**: Optional logout when no more materials are available
- **Debug Mode**: Enable debug information for troubleshooting

## Requirements

- **Location**: Start at Edgeville bank or near Edgeville furnace
- **Materials**: Required ores and coal in your bank for the selected bar type
- **Smithing Level**: Appropriate smithing level for the selected bar type

## Bar Requirements

| Bar Type | Required Level | Materials |
|----------|----------------|-----------|
| Bronze | 1 | Copper ore + Tin ore |
| Iron | 15 | Iron ore |
| Silver | 20 | Silver ore |
| Steel | 30 | Iron ore + Coal |
| Gold | 40 | Gold ore |
| Mithril | 50 | Mithril ore + 4 Coal |
| Adamantite | 70 | Adamantite ore + 6 Coal |
| Runite | 85 | Runite ore + 8 Coal |

## Configuration Options

- **Bar Type**: Select which type of bar to smelt
- **Debug Mode**: Enable/disable debug information in overlay
- **Logout When Complete**: Automatically logout when no more materials are available

## How to Use

1. **Prepare Materials**: Ensure you have the required ores and coal in your bank
2. **Start Location**: Begin at Edgeville bank or near the Edgeville furnace
3. **Configure Plugin**: Select your desired bar type and options
4. **Start Plugin**: Enable the Auto Smelt plugin
5. **Monitor Progress**: The plugin will handle banking and smelting automatically

## Important Notes

- The plugin uses the Edgeville furnace (requires completion of Varrock Medium Diary for best results)
- Make sure you have sufficient inventory space for the materials
- The plugin will stop if materials run out (with optional logout)
- Walking between bank and furnace is handled automatically

## Disclaimer

This plugin is for educational purposes. Use of automation tools may violate game terms of service.
