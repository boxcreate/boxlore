# Announcement images

Images that announcements show in the app's alert and in the system notification. The app loads an announcement's image from its `https` link when the alert opens, which can be days after it was sent, so a file here is never renamed, moved or replaced. A new image gets a new file.

Link an image by the commit that added it, `https://raw.githubusercontent.com/boxcreate/boxlore/<commit>/docs/announcements/<file>`, so the link does not depend on a branch. Images are 16:9 (1600 by 900). The alert shows the whole image as a banner. In the system notification Android crops the image to fit, and how much it keeps depends on the device and Android version; the phones checked so far show about the middle 2:1, so keep text and anything important near the middle. Keep files under 300 KB.

| File | Announcement | Sent |
| :--- | :--- | :--- |
| `2026-11-web-beta.jpg` | boxlore is now on the web (boxlore.in beta) | November 2026 |
