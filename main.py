"""
Smart Web Scraper v4 — Kivy Android App
Scrapes product title, price, image, and URL from e-commerce sites.
Saves images + Excel to /sdcard/Download/termux_stuff/
"""

import os
import re
import threading
from urllib.parse import urljoin, urlparse, urlencode, parse_qs, urlunparse

import requests
from bs4 import BeautifulSoup

try:
    import pandas as pd
    PANDAS_OK = True
except ImportError:
    import csv
    PANDAS_OK = False

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.scrollview import ScrollView
from kivy.clock import Clock
from kivy.core.window import Window

# ── Android permissions ──────────────────────────────────────────────────────
try:
    from android.permissions import request_permissions, Permission
    _ANDROID = True
except ImportError:
    _ANDROID = False

# ── Save paths ───────────────────────────────────────────────────────────────
_PRIMARY_SAVE = '/sdcard/Download/termux_stuff'
_IMAGES_SUBDIR = 'images'

# ── Scraper constants (mirror of ScraperEngine.kt) ───────────────────────────
PRODUCT_SELECTORS = [
    'salla-product-card',      # Salla platform — highest priority
    'div.product-grid-item',
    'li.product',
    'div.salla-product-card',
    'div.product-card',
    'div.product-item',
    'div.product-box',         # Zid platform
    'article.product',
]

TITLE_TAGS = ['h2', 'h3', 'h1', 'h4', 'a', 'strong']
TITLE_CLASSES = [
    'product-title',
    'woocommerce-loop-product__title',
    'title',
    'product-name',
    'product-item-link',
    'salla-product-card__title',
]

PRICE_CLASSES = [
    'salla-product-card__price',
    'woocommerce-Price-amount',
    'product-price',
    'price',
]

BAD_TITLE_WORDS = ['تفاصيل', 'تخفيض',
                   'Sale', 'خصم', 'اتصل',
                   'تواصِل', 'قائمة']

IMAGE_ATTRS = ['data-lazy-src', 'data-src', 'data-original',
               'data-lazy', 'srcset', 'data-srcset', 'src']

BAD_IMAGE_KW = ['logo', 'شعار', 'data:image', 'placeholder',
                'banner', 'icon', 'sprite', 'loading', 'blank', 'noimage', 'no-image']

HTTP_HEADERS = {
    'User-Agent': ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                   'AppleWebKit/537.36 (KHTML, like Gecko) '
                   'Chrome/120.0.0.0 Safari/537.36'),
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'ar,en-US;q=0.7,en;q=0.3',
}

MAX_PAGES = 20   # upper limit; stops early when no products found


# ════════════════════════════════════════════════════════════════════════════
#  Scraping helpers
# ════════════════════════════════════════════════════════════════════════════

def _make_session():
    s = requests.Session()
    s.headers.update(HTTP_HEADERS)
    return s


def _fetch(session, url, referer):
    try:
        r = session.get(url, timeout=15, headers={'Referer': referer}, allow_redirects=True)
        r.raise_for_status()
        return r.text
    except Exception:
        return None


def _find_products(soup):
    for sel in PRODUCT_SELECTORS:
        if '.' in sel:
            tag, cls = sel.split('.', 1)
            found = soup.find_all(tag, class_=cls)
        else:
            found = soup.find_all(sel)
        if found:
            return found
    found = soup.find_all('div', class_=lambda c: c and 'product' in c.lower())
    if found:
        return found
    return soup.find_all('li', class_=lambda c: c and 'product' in c.lower())


def _bad_title(text):
    return any(w in text for w in BAD_TITLE_WORDS)


def _extract_title(el):
    if el.name == 'salla-product-card':
        n = el.get('name', '').strip()
        if n and not _bad_title(n):
            return n
    for tag in TITLE_TAGS:
        for cls in TITLE_CLASSES:
            found = el.find(tag, class_=cls)
            if found:
                t = found.get_text(strip=True)
                if t and not _bad_title(t):
                    return t
    for h in el.find_all(['h2', 'h3', 'h4']):
        t = h.get_text(strip=True)
        if t and not _bad_title(t):
            return t
    return None


def _extract_price(el):
    if el.name == 'salla-product-card':
        p = el.get('price', '').strip()
        if p:
            return p
    for cls in PRICE_CLASSES:
        found = el.find(class_=cls)
        if found:
            t = found.get_text(strip=True)
            if t:
                return t
    found = el.find(class_=lambda c: c and 'price' in c.lower())
    return found.get_text(strip=True) if found else ''


def _extract_url(el, base):
    if el.name == 'salla-product-card':
        for attr in ('href', 'url', 'product-url'):
            v = el.get(attr, '').strip()
            if v:
                return urljoin(base, v)
    link = el.find('a', href=True)
    return urljoin(base, link['href']) if link else ''


def _bad_image(url):
    low = url.lower()
    return any(kw in low for kw in BAD_IMAGE_KW)


def _extract_image(el, base):
    if el.name == 'salla-product-card':
        th = el.get('thumbnail', '').strip()
        if th and not _bad_image(th):
            return urljoin(base, th)
    img = el.find('img')
    if not img:
        return None
    for attr in IMAGE_ATTRS:
        val = img.get(attr, '').strip()
        if val:
            candidate = val.split()[0]
            if candidate and not _bad_image(candidate):
                return urljoin(base, candidate)
    return None


def _page_url(base, page):
    if page == 1:
        return base
    p = urlparse(base)
    is_woo = 'product-category' in p.path or '/shop' in p.path
    if is_woo:
        if '/page/' in p.path:
            new_path = re.sub(r'page/\d+/', f'page/{page}/', p.path)
        else:
            new_path = p.path.rstrip('/') + f'/page/{page}/'
        return urlunparse(p._replace(path=new_path, query=''))
    else:
        params = parse_qs(p.query, keep_blank_values=True)
        params['page'] = [str(page)]
        new_q = urlencode({k: v[0] for k, v in params.items()})
        return urlunparse(p._replace(query=new_q))


def scrape_site(base_url, log_fn):
    session = _make_session()
    products = []
    seen = set()

    for page in range(1, MAX_PAGES + 1):
        url = _page_url(base_url, page)
        log_fn(f'[{page}] {url}')

        html = _fetch(session, url, base_url)
        if not html:
            log_fn(f'  ⛔ Failed to load page {page}. Stopping.')
            break

        soup = BeautifulSoup(html, 'lxml')
        elements = _find_products(soup)

        if not elements:
            log_fn(f'  ℹ No products on page {page}.')
            break

        log_fn(f'  🔍 {len(elements)} elements found. Extracting…')
        page_new = 0

        for el in elements:
            title = _extract_title(el)
            if not title or title in seen:
                continue
            seen.add(title)

            image_url = _extract_image(el, base_url)
            if not image_url:
                continue

            products.append({
                'title': title,
                'price': _extract_price(el),
                'product_url': _extract_url(el, base_url),
                'image_url': image_url,
                'local_image': '',
            })
            page_new += 1
            log_fn(f'  ✓ {title[:45]}')

        if page_new == 0:
            log_fn('  ℹ No new products. Stopping pagination.')
            break

    return products


# ════════════════════════════════════════════════════════════════════════════
#  Save helpers
# ════════════════════════════════════════════════════════════════════════════

def _resolve_save_dir():
    """Return save dir; fall back to app internal storage if /sdcard is blocked."""
    try:
        os.makedirs(_PRIMARY_SAVE, exist_ok=True)
        probe = os.path.join(_PRIMARY_SAVE, '.probe')
        with open(probe, 'w') as f:
            f.write('ok')
        os.remove(probe)
        return _PRIMARY_SAVE
    except OSError:
        fallback = os.path.join(App.get_running_app().user_data_dir, 'termux_stuff')
        os.makedirs(fallback, exist_ok=True)
        return fallback


def save_results(products, log_fn):
    save_dir = _resolve_save_dir()
    img_dir = os.path.join(save_dir, _IMAGES_SUBDIR)
    os.makedirs(img_dir, exist_ok=True)

    session = _make_session()
    total = len(products)

    for i, p in enumerate(products, 1):
        log_fn(f'⬇ Image {i}/{total}…')
        try:
            r = session.get(p['image_url'], timeout=20)
            if r.ok:
                raw_ext = p['image_url'].split('?')[0].rsplit('.', 1)[-1][:4]
                ext = raw_ext if raw_ext.isalpha() else 'jpg'
                fname = f'product_{i}.{ext}'
                with open(os.path.join(img_dir, fname), 'wb') as f:
                    f.write(r.content)
                p['local_image'] = fname
        except Exception as e:
            log_fn(f'  ⚠ Image {i} failed: {e}')

    cols = ['title', 'price', 'product_url', 'image_url', 'local_image']

    if PANDAS_OK:
        xlsx_path = os.path.join(save_dir, 'products.xlsx')
        df = pd.DataFrame(products, columns=cols)
        df.index = df.index + 1   # 1-based row numbers
        df.to_excel(xlsx_path, index_label='#')
        log_fn(f'📊 Excel → {xlsx_path}')
    else:
        csv_path = os.path.join(save_dir, 'products.csv')
        with open(csv_path, 'w', newline='', encoding='utf-8') as f:
            w = csv.DictWriter(f, fieldnames=cols)
            w.writeheader()
            w.writerows(products)
        log_fn(f'📄 CSV → {csv_path}  (Excel library not available)')

    log_fn(f'✅ Done! {total} products saved to: {save_dir}')
    log_fn(f'🖼 Images → {img_dir}')


# ════════════════════════════════════════════════════════════════════════════
#  Kivy App
# ════════════════════════════════════════════════════════════════════════════

class SmartScraperApp(App):

    def build(self):
        Window.clearcolor = (0.12, 0.12, 0.12, 1)

        root = BoxLayout(orientation='vertical', padding=12, spacing=8)

        # Title bar
        root.add_widget(Label(
            text='Smart Web Scraper v4',
            size_hint_y=None, height=48,
            font_size='18sp', bold=True,
            color=(0.4, 0.8, 1, 1),
        ))

        # URL input
        self.url_input = TextInput(
            text='https://',
            hint_text='Enter website / store URL',
            size_hint_y=None, height=46,
            multiline=False,
            font_size='14sp',
            background_color=(0.18, 0.18, 0.18, 1),
            foreground_color=(1, 1, 1, 1),
            cursor_color=(0.4, 0.8, 1, 1),
        )
        root.add_widget(self.url_input)

        # Button
        self.btn = Button(
            text='START SCRAPING',
            size_hint_y=None, height=50,
            font_size='16sp', bold=True,
            background_color=(0.15, 0.55, 0.95, 1),
            background_normal='',
        )
        self.btn.bind(on_press=self.start_scraping)
        root.add_widget(self.btn)

        # Log area
        scroll = ScrollView(size_hint=(1, 1))
        self.log_box = TextInput(
            text='Ready.\nEnter a URL above and press START SCRAPING.\n',
            readonly=True,
            multiline=True,
            font_size='13sp',
            background_color=(0.08, 0.08, 0.08, 1),
            foreground_color=(0.85, 0.85, 0.85, 1),
            size_hint_y=None,
        )
        self.log_box.bind(minimum_height=self.log_box.setter('height'))
        scroll.add_widget(self.log_box)
        root.add_widget(scroll)
        self._scroll = scroll

        return root

    def on_start(self):
        if _ANDROID:
            request_permissions([
                Permission.WRITE_EXTERNAL_STORAGE,
                Permission.READ_EXTERNAL_STORAGE,
            ])

    # ── Button handler ───────────────────────────────────────────────────────

    def start_scraping(self, _instance):
        url = self.url_input.text.strip()
        if not url or url in ('https://', 'http://'):
            self._append_log('❌ Please enter a valid URL.\n')
            return
        if not url.startswith(('http://', 'https://')):
            url = 'https://' + url

        self.btn.disabled = True
        self.btn.text = 'SCRAPING…'
        self.log_box.text = f'Starting: {url}\n'
        threading.Thread(target=self._worker, args=(url,), daemon=True).start()

    # ── Background worker ────────────────────────────────────────────────────

    def _worker(self, url):
        try:
            products = scrape_site(url, self._log)

            if not products:
                self._log(
                    '⚠ No products found.\n'
                    'Possible reasons:\n'
                    '  • Site renders products via JavaScript (dynamic content)\n'
                    '  • Site uses non-standard HTML structure\n'
                    '  • Bot protection / rate limiting active\n'
                    'Try a different page URL (category page, not homepage).'
                )
            else:
                self._log(f'\n📦 {len(products)} products found. Saving…')
                save_results(products, self._log)

        except Exception as e:
            self._log(f'❌ Unexpected error: {e}')
        finally:
            Clock.schedule_once(self._reset_button)

    def _reset_button(self, _dt):
        self.btn.disabled = False
        self.btn.text = 'START SCRAPING'

    # ── Thread-safe logging ──────────────────────────────────────────────────

    def _log(self, msg):
        Clock.schedule_once(lambda dt: self._append_log(msg + '\n'))

    def _append_log(self, text):
        self.log_box.text += text
        # scroll to bottom
        Clock.schedule_once(lambda dt: setattr(self._scroll, 'scroll_y', 0), 0.05)


if __name__ == '__main__':
    SmartScraperApp().run()
