const fs = require('fs');
const path = require('path');

const layoutFile = 'D:/AProject Fluxibiz/fluxibiz-front/ipos-frontend/src/app/store/[slug]/layout.tsx';

if (!fs.existsSync(layoutFile)) {
  console.log('Store layout file not found!');
  process.exit(1);
}

let content = fs.readFileSync(layoutFile, 'utf8');

if (!content.includes('TelegramWebAppProvider')) {
  content = `import TelegramWebAppProvider from "@/components/tma/TelegramWebAppProvider";\nimport { Suspense } from "react";\n` + content;
  
  content = content.replace(
    '{children}',
    '<Suspense fallback={null}><TelegramWebAppProvider>{children}</TelegramWebAppProvider></Suspense>'
  );

  fs.writeFileSync(layoutFile, content, 'utf8');
  console.log('STORE_LAYOUT_WRAPPED_WITH_TELEGRAM_WEB_APP_PROVIDER');
} else {
  console.log('STORE_LAYOUT_ALREADY_WRAPPED');
}