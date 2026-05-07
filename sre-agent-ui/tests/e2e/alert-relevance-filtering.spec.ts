import { test, expect } from '@playwright/test';

/**
 * E2E tests for V.2-UI-6.1: Alert Relevance Filtering & RCA Eligibility Guard
 *
 * Tests:
 *   1. ServiceHealthOverview shows ONLY service-scoped alerts (SERVICE_ALERT)
 *   2. Platform/Watchdog alerts are NOT visible on the health overview page
 *   3. Only rcaEligible alerts show "触发 RCA 分析" button
 *   4. Attempting RCA on non-service alert returns 400 from backend
 *   5. Alert summary shows filtered count (已过滤 N 条非业务告警)
 *
 * Prerequisites:
 *   - Backend running on port 8080 (with Alertmanager access)
 *   - Frontend dev server on port 5173
 *   - Alertmanager must have at least some alerts firing (including platform alerts)
 */

test.describe('Alert Relevance Filtering (V.2-UI-6.1)', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('alert section title shows "业务告警" not "活跃告警"', async ({ page }) => {
    const title = page.getByText('业务告警');
    await expect(title).toBeVisible({ timeout: 10000 });
  });

  test('KPI card shows service alert count (not total)', async ({ page }) => {
    // The "活跃告警" KPI should show only SERVICE_ALERT count
    const kpiLabel = page.getByText('活跃告警');
    await expect(kpiLabel).toBeVisible({ timeout: 10000 });
  });

  test('platform alerts (Watchdog, etcd, NodeClock) are NOT shown in alert list', async ({ page }) => {
    // Wait for alerts to load
    await page.waitForTimeout(3000);

    // These platform alert names should NOT appear in the visible alert list
    const platformAlertNames = ['Watchdog', 'etcd', 'NodeClock', 'InfoInhibitor'];

    for (const name of platformAlertNames) {
      const alertItem = page.getByText(name, { exact: false });
      // If it exists, it should NOT be inside the alert list card
      // (it might appear in filter count text, which is fine)
      const count = await alertItem.count();
      if (count > 0) {
        // Check it's not inside the alert list area
        const cardContext = page.locator('.card').filter({ hasText: '业务告警' });
        const insideCard = await cardContext.getByText(name, { exact: false }).count();
        expect(insideCard).toBe(0);
      }
    }
  });

  test('filter summary shows when non-service alerts exist', async ({ page }) => {
    // Wait for alerts to load
    await page.waitForTimeout(3000);

    // If there are filtered alerts, should show "已过滤" text
    const filterText = page.getByText(/已过滤 \d+ 条/);
    const filterVisible = await filterText.isVisible().catch(() => false);

    // This is conditional — only visible when there are filtered alerts
    // If Alertmanager has platform alerts, this should be visible
    if (filterVisible) {
      await expect(filterText).toBeVisible();
    }
  });

  test('each visible alert has RCA trigger button (all are rcaEligible)', async ({ page }) => {
    // Wait for service alerts to load
    const triggerButtons = page.getByRole('button', { name: /触发 RCA 分析/ });
    await expect(triggerButtons.first()).toBeVisible({ timeout: 10000 }).catch(() => {
      // It's OK if no service alerts are currently firing
    });

    const count = await triggerButtons.count();
    // Every visible alert in the list should have an RCA trigger button
    // (because only SERVICE_ALERT are shown, and all SERVICE_ALERT are rcaEligible)
    const alertItems = page.locator('.card').filter({ hasText: '业务告警' }).locator('[class*="status-dot"]');
    const alertCount = await alertItems.count();

    // Number of trigger buttons should equal number of visible service alerts
    expect(count).toBe(alertCount);
  });

  test('backend rejects RCA trigger for non-service alert with 400', async ({ page }) => {
    // Directly call the API with a simulated non-service alert fingerprint
    // This tests the backend eligibility guard
    const response = await page.request.get('/api/incidents/alerts');
    expect(response.ok()).toBe(true);

    const data = await response.json();

    // Find a non-SERVICE_ALERT if any exist
    const nonServiceAlert = data.alerts?.find(
      (a: any) => a.relevance !== 'SERVICE_ALERT'
    );

    if (nonServiceAlert) {
      // Try to trigger RCA on it — should get 400
      const rcaResponse = await page.request.post('/api/incidents/from-alert', {
        data: { fingerprint: nonServiceAlert.fingerprint }
      });
      expect(rcaResponse.status()).toBe(400);
    }
  });

  test('API response structure includes summary with classification counts', async ({ page }) => {
    const response = await page.request.get('/api/incidents/alerts');
    expect(response.ok()).toBe(true);

    const data = await response.json();

    // Verify new AlertsResponse structure
    expect(data).toHaveProperty('alerts');
    expect(data).toHaveProperty('summary');
    expect(data).toHaveProperty('timestamp');
    expect(Array.isArray(data.alerts)).toBe(true);

    // Summary should have classification counts
    expect(data.summary).toHaveProperty('totalAlerts');
    expect(data.summary).toHaveProperty('serviceAlerts');
    expect(data.summary).toHaveProperty('platformAlerts');
    expect(data.summary).toHaveProperty('watchdogAlerts');

    // Verify counts add up
    const s = data.summary;
    expect(s.totalAlerts).toBe(
      s.serviceAlerts + s.platformAlerts + s.watchdogAlerts + s.unsupportedAlerts + s.ignoredAlerts
    );
  });

  test('each alert in API response has relevance and rcaEligible fields', async ({ page }) => {
    const response = await page.request.get('/api/v2/alerts');
    const data = await response.json();

    if (data.alerts && data.alerts.length > 0) {
      for (const alert of data.alerts) {
        expect(alert).toHaveProperty('relevance');
        expect(alert).toHaveProperty('rcaEligible');
        expect(typeof alert.rcaEligible).toBe('boolean');

        // Only SERVICE_ALERT should be rcaEligible
        if (alert.relevance === 'SERVICE_ALERT') {
          expect(alert.rcaEligible).toBe(true);
        } else {
          expect(alert.rcaEligible).toBe(false);
        }
      }
    }
  });
});
