const fs = require('fs');
const https = require('https');
const path = require('path');

const dest = path.resolve('app/src/main/res/font/noto_nastaliq_urdu.ttf');
fs.mkdirSync(path.dirname(dest), { recursive: true });

const file = fs.createWriteStream(dest);
https.get('https://raw.githubusercontent.com/googlefonts/noto-fonts/main/hinted/ttf/NotoNastaliqUrdu/NotoNastaliqUrdu-Regular.ttf', (response) => {
    response.pipe(file);
    file.on('finish', () => {
        file.close();
        console.log('Download completed');
    });
}).on('error', (err) => {
    fs.unlink(dest, () => {});
    console.error('Download failed:', err.message);
});
