# Pagertron
Push to GitHub -> Actions builds `pager-debug-apk`. Needs Google Play services on the phone (for the mesh radio).
Two phones with the app + Radio ON (FUNC > RADIO) will link automatically.

## v2.0
- **Landscape**: every pager has a landscape layout. A `ROT` key on each device (and FUNC > DISPLAY) turns the phone itself, even with rotation lock on, and the device animates into place.
- **CM Qwerty** is the default, free and simplest pager. Gems: +1 for every message sent or received (plus the daily one).
- **CM P1** (landscape QWERTY, scroll roller, green Nokia-style backlight, centre LED: green -> yellow -> orange -> red by message count).
- **CM Paper Pager** (resistive touchscreen, 128x256 e-ink, buttons on the sides, stylus compartment, sends drawings as 128x256 1-bit images).
- All pagers can view images: scroll bar on the LCD, arrow keys (▲▼) or a scroll wheel (Motor Roller, Blueberry, CM P1).
- FUNC > DEBUG > "INJECT IMAGE 128x256" shows a test image without a second phone.
