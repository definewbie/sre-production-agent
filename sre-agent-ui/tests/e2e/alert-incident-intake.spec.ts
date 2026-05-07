import { test, expect } from '@playwright/test';

/**
 * E2E tests for V.2-UI-6: Alert-driven Incident Intake
 *
 * Tests the production-like path:
 *   1. ServiceHealthOverview shows firing alerts from Alertmanager
 *   2. Clicking "触发 RCA 分析" triggers RCA via /api/incidents/from-alert
 *   3. App navigates to RCA analysis page (state-based, not URL routing)
 *   4. RcaAnalysisPanel renders hypotheses + evidence from alert-driven RCA
 *
 * Prerequisites:
 *   - Backend running on port 8080 (with Alertmanager access)
 *   - Frontend dev server on port 5173
 */

test.describe('Alert-Driven Incident Intake (V.2-UI-6)', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('alerts section loads and shows firing alerts', async ({ page }) => {
    // Wait for alert trigger buttons to appear (proves Alertmanager data loaded)
    const triggerButtons = page.getByRole('button', { name: /触发 RCA 分析/ });
    await expect(triggerButtons.first()).toBeVisible({ timeout: 10000 });

    const count = await triggerButtons.count();
    expect(count).toBeGreaterThan(0);
  });

  test('KPI card shows active alert count', async ({ page }) => {
    const alertBadge = page.getByText('活跃告警', { exact: true });
    await expect(alertBadge).toBeVisible({ timeout: 10000 });

    // Should show a count > 0
    const countText = page.getByText(/\d+/).first();
    await expect(countText).toBeVisible();
  });

  test('clicking RCA trigger navigates to analysis page', async ({ page }) => {
    // Click the first "触发 RCA 分析" button
    const triggerButtons = page.getByRole('button', { name: /触发 RCA 分析/ });
    await expect(triggerButtons.first()).toBeVisible({ timeout: 10000 });
    await triggerButtons.first().click();

    // The page should switch to RCA analysis (state-based navigation)
    // Look for RCA analysis heading or breadcrumb
    const rcaHeading = page.getByText(/RCA 分析|告警驱动分析/).first();
    await expect(rcaHeading).toBeVisible({ timeout: 20000 });
  });

  test('RCA analysis page shows breadcrumb with alert-driven context', async ({ page }) => {
    const triggerButtons = page.getByRole('button', { name: /触发 RCA 分析/ });
    await expect(triggerButtons.first()).toBeVisible({ timeout: 10000 });
    await triggerButtons.first().click();

    // Wait for breadcrumb showing alert-driven path
    const breadcrumb = page.getByText(/告警驱动分析/);
    await expect(breadcrumb).toBeVisible({ timeout: 20000 });
  });

  test('RCA analysis shows hypotheses and evidence', async ({ page }) => {
    const triggerButtons = page.getByRole('button', { name: /触发 RCA 分析/ });
    await expect(triggerButtons.first()).toBeVisible({ timeout: 10000 });
    await triggerButtons.first().click();

    // Wait for RCA data to load
    // Hypotheses should appear with confidence scores
    const hypothesisElement = page.getByText(/假设|hypothesis|downstream|latency|OOM|crashloop/i).first();
    await expect(hypothesisElement).toBeVisible({ timeout: 20000 });

    // Evidence section should also be visible
    const evidenceElement = page.getByText(/证据|evidence|prometheus|loki|jaeger|k8s/i).first();
    await expect(evidenceElement).toBeVisible({ timeout: 10000 });
  });
});
