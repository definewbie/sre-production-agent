#!/usr/bin/env python3
"""E2E test: EnvironmentStatusPanel + Sidebar env summary via Vite proxy."""
import json
import sys
import time
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
PASS = 0
FAIL = 0

def ok(name):
    global PASS
    PASS += 1
    print(f'  \u2713 {name}')

def fail(name, detail=''):
    global FAIL
    FAIL += 1
    print(f'  \u2717 {name} -- {detail}')

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 900})

    # ---- Test 1: App loads ----
    print('\n[Test 1] App loads')
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    title = page.title()
    if 'SRE' in title:
        ok(f'page title: {title}')
    else:
        fail(f'unexpected title: {title}')

    # ---- Test 2: Navigate to Environment Status (index 4) ----
    print('\n[Test 2] Navigate to Environment Status')
    navs = page.locator('.nav-item')
    count = navs.count()
    if count >= 5:
        navs.nth(4).click()
        page.wait_for_load_state('networkidle')
        time.sleep(2)
        heading = page.locator('h1.page-title').first
        ht = heading.text_content().strip()
        if '环境状态' in ht:
            ok(f'heading: {ht}')
        else:
            fail(f'unexpected heading: {ht}')
    else:
        fail(f'nav items={count}, expected >=5')

    # ---- Test 3: KPI cards ----
    print('\n[Test 3] KPI cards')
    cards = page.locator('.card')
    # Find KPI cards (the flex row with stats)
    kpi_texts = []
    all_cards_text = ''
    for i in range(min(cards.count(), 10)):
        t = cards.nth(i).text_content().strip()
        all_cards_text += t + ' | '
    if '组件总数' in all_cards_text:
        ok('组件总数 card present')
    else:
        fail('missing 组件总数 card')
    if '正常组件' in all_cards_text:
        ok('正常组件 card present')
    else:
        fail('missing 正常组件 card')
    if '异常组件' in all_cards_text:
        ok('异常组件 card present')
    else:
        fail('missing 异常组件 card')
    if '最近检查' in all_cards_text:
        ok('最近检查 card present')
    else:
        fail('missing 最近检查 card')
    if '环境状态' in all_cards_text:
        ok('环境状态 card present')
    else:
        fail('missing 环境状态 card')

    # ---- Test 4: Table renders with rows ----
    print('\n[Test 4] Table renders')
    table = page.locator('.data-table').first
    rows = table.locator('tbody tr')
    rc = rows.count()
    if rc >= 7:
        ok(f'{rc} rows rendered (expected 8)')
    elif rc >= 1:
        fail(f'only {rc} rows, expected ~8')
    else:
        fail('table has no rows')

    # ---- Test 5: Badge classes ----
    print('\n[Test 5] Badge classes')
    # Re-query badges in the table
    table_badges = table.locator('.badge')
    bc = table_badges.count()
    ok(f'{bc} badge elements in table')
    
    has_both_classes = True
    for i in range(bc):
        cls = table_badges.nth(i).get_attribute('class') or ''
        # Should have 'badge' + specific color
        parts = cls.split()
        if 'badge' not in parts:
            has_both_classes = False
            fail(f'badge missing base class: {cls}')
            break
    if has_both_classes and bc > 0:
        ok('all badges have base .badge class')

    green_count = 0
    red_count = 0
    for i in range(bc):
        cls = table_badges.nth(i).get_attribute('class') or ''
        if 'badge-green' in cls:
            green_count += 1
        if 'badge-red' in cls:
            red_count += 1
    ok(f'{green_count} green badges, {red_count} red badges')
    if green_count >= 4:
        ok('healthy components shown in green')
    if red_count >= 2:
        ok('down components shown in red')

    # ---- Test 6: Sidebar env summary ----
    print('\n[Test 6] Sidebar env summary')
    # Stay on env page, check sidebar
    sidebar = page.locator('.sidebar')
    if sidebar.count() > 0:
        env_summary = sidebar.locator('.env-summary')
        if env_summary.count() > 0:
            ok('env-summary section in sidebar')
            text = env_summary.text_content().strip()[:100]
            ok(f'content: {text}')
        else:
            # Check if sidebar has component dots/names
            sidebar_html = sidebar.inner_html()
            if 'Prometheus' in sidebar_html:
                ok('sidebar has Prometheus ref')
            else:
                fail('no env summary in sidebar', sidebar_html[:300])
    else:
        fail('no sidebar element')

    # ---- Test 7: Sidebar status dots ----
    print('\n[Test 7] Sidebar status dots')
    dots = page.locator('.env-dot, .status-dot, .sidebar .dot')
    dc = dots.count()
    if dc >= 4:
        ok(f'{dc} status dots in sidebar')
    else:
        # Check for inline colored circles in sidebar
        sidebar_spans = sidebar.locator('span')
        colored = 0
        for i in range(min(sidebar_spans.count(), 30)):
            style = sidebar_spans.nth(i).get_attribute('style') or ''
            if 'background' in style and ('green' in style or 'red' in style or 'orange' in style):
                colored += 1
        if colored >= 4:
            ok(f'{colored} colored dots via inline style')
        else:
            fail(f'only {dc} dots / {colored} colored spans')

    # ---- Test 8: Refresh button ----
    print('\n[Test 8] Refresh button')
    refresh_btn = page.locator('button').filter(has_text='刷新')
    if refresh_btn.count() > 0:
        refresh_btn.first.click()
        time.sleep(2)
        ok('refresh button clicked')
        rows2 = page.locator('.data-table tbody tr')
        if rows2.count() >= 1:
            ok(f'table has {rows2.count()} rows after refresh')
        else:
            fail('table empty after refresh')
    else:
        fail('no refresh button')

    # ---- Test 9: Health check button ----
    print('\n[Test 9] Health check button')
    check_btn = page.locator('button').filter(has_text='运行健康检查')
    if check_btn.count() > 0:
        ok('health check button found')
        check_btn.first.click()
        time.sleep(3)
        ok('health check button clicked')
    else:
        fail('no health check button')

    # ---- Test 10: Other pages still render ----
    print('\n[Test 10] Other pages render')
    # Service Health (index 0)
    navs.nth(0).click()
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    h = page.locator('h1.page-title').first
    if h.count() > 0 and '服务健康' in h.text_content():
        ok('Service Health page renders')
    else:
        fail('Service Health page missing title')

    browser.close()

print(f'\n{"="*50}')
print(f'Results: {PASS} passed, {FAIL} failed')
sys.exit(1 if FAIL > 0 else 0)
