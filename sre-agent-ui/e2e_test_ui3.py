#!/usr/bin/env python3
"""E2E test: ServiceHealthOverview real API integration."""
import json, sys, time
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
PASS = 0
FAIL = 0

def ok(name):
    global PASS; PASS += 1; print(f'  PASS  {name}')

def fail(name, detail=''):
    global FAIL; FAIL += 1; print(f'  FAIL  {name} {detail}')

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1400, 'height': 900})

    # --- Test 1: App loads ---
    page.goto(BASE, wait_until='networkidle', timeout=15000)
    ok('App loads')

    # --- Test 2: Navigate to Service Health (page 0) ---
    nav_items = page.locator('.nav-item')
    nav_items.nth(0).click()
    page.wait_for_timeout(1500)
    ok('Navigate to service health page')

    # --- Test 3: Page title ---
    title = page.locator('h1.page-title')
    if title.count() > 0 and '服务健康总览' in title.first.text_content():
        ok('Page title: 服务健康总览')
    else:
        fail('Page title', f'got: {title.first.text_content() if title.count() > 0 else "none"}')

    # --- Test 4: KPI cards ---
    kpi_labels = page.locator('.kpi-label')
    kpi_found = 0
    for i in range(kpi_labels.count()):
        txt = kpi_labels.nth(i).text_content() or ''
        if any(k in txt for k in ['服务总数', '异常服务', '健康服务', '告警数', '影响用户']):
            kpi_found += 1
    if kpi_found >= 4:
        ok(f'KPI cards present ({kpi_found})')
    else:
        fail('KPI cards', f'found {kpi_found}')

    # --- Test 5: KPI values from real API (total=3) ---
    kpi_vals = page.locator('.kpi-value')
    total_text = kpi_vals.nth(0).text_content() if kpi_vals.count() > 0 else ''
    if total_text.strip() == '3':
        ok(f'服务总数 = 3 (from real API)')
    else:
        fail('服务总数', f'got: "{total_text.strip()}"')

    # --- Test 6: Service table rows = 3 ---
    rows = page.locator('.data-table tbody tr')
    if rows.count() == 3:
        ok(f'Service table: 3 rows')
    else:
        fail('Service table rows', f'got: {rows.count()}')

    # --- Test 7: Real service names present ---
    body_text = page.locator('.data-table tbody').text_content() or ''
    for svc in ['order-service', 'payment-service', 'inventory-service']:
        if svc in body_text:
            ok(f'Service name: {svc}')
        else:
            fail(f'Service name: {svc}', 'not found in table')

    # --- Test 8: Status badges present ---
    badges = page.locator('.data-table .badge')
    if badges.count() >= 3:
        ok(f'Status badges: {badges.count()} found')
    else:
        fail('Status badges', f'count: {badges.count()}')

    # --- Test 9: Mock indicator visible ---
    page_text = page.locator('body').text_content() or ''
    if 'Mock' in page_text or 'mock' in page_text:
        ok('Mock indicator visible')
    else:
        fail('Mock indicator', 'no Mock label found')

    # --- Test 10: Mock column headers marked with M ---
    ths = page.locator('.data-table thead th')
    mock_headers = 0
    for i in range(ths.count()):
        txt = ths.nth(i).inner_html() or ''
        if '<sup' in txt and 'M' in txt:
            mock_headers += 1
    if mock_headers >= 3:
        ok(f'Mock column markers: {mock_headers} columns marked with M')
    else:
        fail('Mock column markers', f'{mock_headers} found')

    # --- Test 11: Topology section present ---
    topo_section = page.locator('.topo-node')
    if topo_section.count() >= 2:
        ok(f'Topology nodes: {topo_section.count()}')
    else:
        fail('Topology nodes', f'count: {topo_section.count()}')

    # --- Test 12: Topology Live label ---
    if 'Live' in page_text:
        ok('Topology source: Live label')
    else:
        fail('Topology source', 'no Live label')

    # --- Test 13: Alerts section with Mock Alerts label ---
    if 'Mock Alerts' in page_text:
        ok('Alerts section: Mock Alerts label')
    else:
        fail('Alerts section', 'no Mock Alerts label')

    # --- Test 14: Refresh button ---
    refresh_btn = page.locator('button:has-text("刷新")')
    if refresh_btn.count() > 0:
        refresh_btn.first.click()
        page.wait_for_timeout(1000)
        ok('Refresh button clickable')
    else:
        fail('Refresh button', 'not found')

    # --- Test 15: Footer note with source explanation ---
    footer = page.locator('.footer-note')
    if footer.count() > 0 and '真实 API' in (footer.first.text_content() or ''):
        ok('Footer source note present')
    else:
        fail('Footer source note')

    # --- Test 16: Other pages unaffected ---
    nav_items.nth(4).click()
    page.wait_for_timeout(800)
    title2 = page.locator('h1.page-title').first.text_content() if page.locator('h1.page-title').count() > 0 else ''
    if '环境状态' in title2:
        ok('Page 环境状态 unaffected')
    else:
        fail('Page 环境状态 unaffected', f'got: {title2}')

    # --- Test 17: Time display present ---
    nav_items.nth(0).click()
    page.wait_for_timeout(800)
    if '更新时间' in page_text or '更新时间' in (page.locator('body').text_content() or ''):
        ok('Update time displayed')
    else:
        fail('Update time', 'not found')

    # --- Test 18: Saturation bars ---
    sat_bars = page.locator('.saturation-bar')
    if sat_bars.count() >= 2:
        ok(f'Saturation bars: {sat_bars.count()}')
    else:
        fail('Saturation bars', f'count: {sat_bars.count()}')

    # --- Test 19: Sparkline SVGs ---
    sparklines = page.locator('.data-table svg')
    if sparklines.count() >= 2:
        ok(f'Sparklines: {sparklines.count()}')
    else:
        fail('Sparklines', f'count: {sparklines.count()}')

    # --- Test 20: Error state (simulate API failure via route override) ---
    page.route('**/api/demo-services/status', lambda route: route.fulfill(status=500, body='Internal Server Error'))
    page.reload(wait_until='networkidle', timeout=10000)
    page.wait_for_timeout(1500)
    err_text = page.locator('body').text_content() or ''
    if '失败' in err_text or '错误' in err_text or '重试' in err_text:
        ok('API failure: error message displayed')
    else:
        fail('API failure', 'no error shown')
    page.unroute('**/api/demo-services/status')

    browser.close()

print(f'\nResults: {PASS}/{PASS+FAIL} passed, {FAIL} failed')
sys.exit(1 if FAIL > 0 else 0)
