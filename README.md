# mdi_radar

A standalone MDI-based radar/map demonstration application.

This project demonstrates extending the MDI map view with radar preset tools,
map-native radar items, ETOPO5 terrain/bathymetry shading, and terrain-aware
line-of-sight visualization.

mdi_radar depends on the MDI library. The ETOPO5 terrain data is loaded from the
MDI classpath resources by `Etopo5Loader`.