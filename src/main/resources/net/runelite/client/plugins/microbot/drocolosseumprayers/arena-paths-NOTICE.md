# Spatial path data

Derived from *A Versatile Dataset of Mouse and Eye Movements on Search Engine
Results Pages* (AdSERP), Kayhan Latifzadeh, Jacek Gwizdka and Luis A. Leiva (2025).
Original mouse data: https://zenodo.org/records/15236546
Authors' repository: https://github.com/kayhan-latifzadeh/AdSERP
License: Creative Commons Attribution 4.0 International (CC BY 4.0).
https://creativecommons.org/licenses/by/4.0/

Changes: retained directed pointer strokes, removed timestamps/dwell/click fields,
rotated and normalized geometry, spatially resampled and smoothed quantization,
and selected 256 paths balanced across contributors within four deviation bands.
Only lateral offsets and the original normalized overshoot magnitude are stored.
Runtime applies stronger arena-specific spatial bounds and exact endpoint landing.
No original personal/session identifiers are distributed in this resource.
