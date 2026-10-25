# NFL LIVE Desktop Web & Installable PWA (Free)

This directory (`desktop-pwa/`) provides a 100% free Desktop Web & Installable Desktop PWA version of **NFL LIVE** for office PCs and laptops.

## Key Features
- **12-Hour AM/PM Time Format**: Displays all hourly (`8:00 AM - 9:00 AM`, `1:00 PM - 2:00 PM`, `2:00 PM - 3:00 PM`) and bi-hourly (`8:00 AM - 10:00 AM`, `1:00 PM - 3:00 PM`, `3:00 PM - 5:00 PM`) slots in familiar 12-hour AM/PM format.
- **Daily Bi-Hourly Audit Tab**: Inside Quality Entry, toggle between **Hourly DHU & Alter (1-Hr)** and **Bi-Hourly Audit (2-Hr OQL)** capturing **Checked Garments (pcs)**, **Defective Garments (pcs)**, and **Defect Breakdown by Type**.
- **Weekly BS OQL & NFL AQC Entry**: Strictly restricted to **Admin** and **Auditor** roles (excluding IE and Quality Manager).
- **1-Click Excel (CSV) Export & Admin Reset**: Export 12-hour AM/PM Production, Quality/Bi-Hourly Audit, and Weekly BS OQL reports or clear demo/operational data.

## How to Run or Host for Free
1. **GitHub Pages (Free)**: Push the repository to GitHub and enable GitHub Pages on `/desktop-pwa` (or copy contents to `/docs`), then open the URL in Chrome or Edge on any office PC and click **Install App** in the address bar.
2. **Firebase Hosting (Free Spark Plan)**: Run `firebase deploy --only hosting` pointing to `desktop-pwa`.
3. **Local Office PC**: Run `python3 -m http.server 8080 --directory desktop-pwa` and open `http://localhost:8080`.
