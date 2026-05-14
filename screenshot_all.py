from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 900})

    page.goto("http://localhost:5173/login", wait_until="networkidle")
    page.locator("input[placeholder='请输入用户名']").fill("admin")
    page.locator("input[placeholder='请输入密码']").fill("password")
    page.locator("button:has-text('登 录')").click()
    page.wait_for_timeout(2000)

    pages = [
        ("01_dashboard", "/dashboard"),
        ("02_travel_list", "/travel"),
        ("03_travel_create", "/travel/create"),
        ("04_report_list", "/report"),
        ("05_report_create", "/report/create"),
        ("06_invoice", "/invoice"),
        ("07_approval", "/approval"),
        ("08_ai_review", "/ai-review"),
        ("09_ai_assistant", "/ai-assistant"),
        ("10_notification", "/notification"),
    ]

    for name, path in pages:
        page.goto(f"http://localhost:5173{path}", wait_until="networkidle")
        page.wait_for_timeout(800)
        page.screenshot(path=f"D:/RecoginitionOCR/screenshots/{name}.png", full_page=False)
        print(f"  {name}: OK")

    browser.close()
    print("\nAll screenshots saved to screenshots/")
