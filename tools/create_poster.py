import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WIDTH = 1920
HEIGHT = 1080

def create_rounded_rect_mask(size, radius):
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), size], radius=radius, fill=255)
    return mask

def draw_bezel_window(base_img, screen_img, pos, size, radius=16, border_color=(255, 255, 255, 45), border_width=2, shadow_offset=15, shadow_blur=25, shadow_alpha=160, badge_text=None, badge_pos="bottom_left", badge_font=None, accent_color=(56, 189, 248)):
    w, h = size
    x, y = pos
    
    # 1. Resize screenshot with antialiasing
    resized = screen_img.resize((w, h), Image.Resampling.LANCZOS)
    
    # 2. Rounded corner mask for the screenshot
    mask = create_rounded_rect_mask((w, h), radius)
    
    # 3. Drop shadow layer
    pad = shadow_blur * 2 + abs(shadow_offset)
    sw = w + pad * 2
    sh = h + pad * 2
    shadow_img = Image.new('RGBA', (sw, sh), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow_img)
    sx0 = pad
    sy0 = pad + shadow_offset
    sdraw.rounded_rectangle([(sx0, sy0), (sx0 + w, sy0 + h)], radius=radius + 2, fill=(0, 0, 0, shadow_alpha))
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(shadow_blur))
    
    # Paste shadow onto base
    base_img.alpha_composite(shadow_img, (x - pad, y - pad))
    
    # 4. Paste rounded screenshot
    base_img.paste(resized, (x, y), mask)
    
    # 5. Draw border and inner bezel glow
    overlay = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    odraw.rounded_rectangle([(0, 0), (w - 1, h - 1)], radius=radius, outline=border_color, width=border_width)
    base_img.alpha_composite(overlay, (x, y))
    
    # 6. Badge pill if provided
    if badge_text and badge_font:
        bbox = badge_font.getbbox(badge_text)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        pw = tw + 28
        ph = th + 14
        
        if badge_pos == "top_left":
            px = x + 18
            py = y + 16
        elif badge_pos == "top_right":
            px = x + w - pw - 18
            py = y + 16
        elif badge_pos == "bottom_left":
            px = x + 18
            py = y + h - ph - 16
        elif badge_pos == "bottom_right":
            px = x + w - pw - 18
            py = y + h - ph - 16
        else: # above
            px = x + 6
            py = y - ph - 8
            
        pill = Image.new('RGBA', (pw, ph), (0, 0, 0, 0))
        pdraw = ImageDraw.Draw(pill)
        pdraw.rounded_rectangle([(0, 0), (pw, ph)], radius=ph // 2, fill=(10, 16, 28, 225), outline=(255, 255, 255, 45), width=1)
        pdraw.ellipse([(10, ph // 2 - 4), (18, ph // 2 + 4)], fill=accent_color)
        pdraw.text((24, (ph - th) // 2 - 2), badge_text, font=badge_font, fill=(240, 245, 255, 245))
        base_img.alpha_composite(pill, (px, py))

def main():
    assets_dir = "/Users/ahmad/.gemini/antigravity/scratch/glass-tv/docs/screenshots"
    font_path = "/Users/ahmad/.gemini/antigravity/scratch/glass-tv/launcher/app/src/main/res/font/outfit.ttf"
    
    # Load fonts
    font_title = ImageFont.truetype(font_path, 42)
    font_subtitle = ImageFont.truetype(font_path, 17)
    font_badge = ImageFont.truetype(font_path, 13)
    font_pill = ImageFont.truetype(font_path, 12)
    font_stat_val = ImageFont.truetype(font_path, 21)
    font_stat_lbl = ImageFont.truetype(font_path, 11)
    font_footer_title = ImageFont.truetype(font_path, 13)
    font_footer_desc = ImageFont.truetype(font_path, 12)
    
    # Create base image RGBA
    img = Image.new('RGBA', (WIDTH, HEIGHT), (8, 13, 24, 255))
    draw = ImageDraw.Draw(img)
    
    # Base dark gradient
    for y in range(HEIGHT):
        ratio = y / HEIGHT
        r = int(9 * (1 - ratio) + 4 * ratio)
        g = int(14 * (1 - ratio) + 6 * ratio)
        b = int(26 * (1 - ratio) + 12 * ratio)
        draw.line([(0, y), (WIDTH, y)], fill=(r, g, b, 255))
        
    # Ambient glowing orbs
    glow_layer = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_layer)
    gdraw.ellipse([( -150, -100), (450, 500)], fill=(56, 189, 248, 45))
    gdraw.ellipse([(1450, -100), (2050, 500)], fill=(16, 185, 129, 40))
    gdraw.ellipse([(600, 250), (1320, 970)], fill=(37, 99, 235, 35))
    gdraw.ellipse([(1300, 700), (2000, 1200)], fill=(245, 158, 11, 35))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(120))
    img.alpha_composite(glow_layer)
    
    # Load Project Screenshots (100% strictly from this project)
    img_home = Image.open(f"{assets_dir}/home-live.png").convert("RGBA")
    img_weather = Image.open(f"{assets_dir}/weather-live.png").convert("RGBA")
    img_azan = Image.open(f"{assets_dir}/azan-popup-live.png").convert("RGBA")
    img_screensaver = Image.open(f"{assets_dir}/screensaver-live.png").convert("RGBA")
    img_wallpaper = Image.open(f"{assets_dir}/wallpaper-dialog-live.png").convert("RGBA")
    
    # --- HEADER SECTION ---
    # Top Pill Badge
    badge_text = "GLASS OS 3.0 • LIVING ROOM OPERATING SYSTEM • ANDROID TV 9-12"
    bbox_b = font_badge.getbbox(badge_text)
    bw = bbox_b[2] - bbox_b[0] + 32
    bh = 28
    badge_img = Image.new('RGBA', (bw, bh), (0, 0, 0, 0))
    bdraw = ImageDraw.Draw(badge_img)
    bdraw.rounded_rectangle([(0, 0), (bw, bh)], radius=bh // 2, fill=(56, 189, 248, 25), outline=(56, 189, 248, 90), width=1)
    bdraw.ellipse([(12, 10), (18, 16)], fill=(56, 189, 248, 255))
    bdraw.text((25, 6), badge_text, font=font_badge, fill=(186, 230, 253, 255))
    img.alpha_composite(badge_img, (70, 40))
    
    # Title & Subtitle
    draw.text((70, 76), "Glass TV Home", font=font_title, fill=(255, 255, 255, 255))
    draw.text((70, 132), "Mindful living room OS featuring Apple Weather Bento, 16:9 Azan Floating Modal & StandBy Clock", font=font_subtitle, fill=(148, 163, 184, 255))
    
    # Right Header Metric Chips
    chips = [
        ("644 KB", "Ultralight Footprint", (52, 211, 153)),
        ("100% Leanback", "D-Pad Remote Ergonomics", (56, 189, 248)),
        ("E-Solat & JAKIM", "Sub-Second Sync", (251, 191, 36))
    ]
    chip_x = 1260
    for val, lbl, col in chips:
        c_box = Image.new('RGBA', (190, 68), (0, 0, 0, 0))
        cdraw = ImageDraw.Draw(c_box)
        cdraw.rounded_rectangle([(0, 0), (190, 68)], radius=12, fill=(15, 23, 42, 170), outline=(255, 255, 255, 25), width=1)
        cdraw.text((16, 11), val, font=font_stat_val, fill=col)
        cdraw.text((16, 40), lbl, font=font_stat_lbl, fill=(148, 163, 184, 230))
        img.alpha_composite(c_box, (chip_x, 80))
        chip_x += 202
        
    # Divider line
    draw.line([(70, 172), (WIDTH - 70, 172)], fill=(255, 255, 255, 30), width=1)
    
    # --- COMPOSITION OF PROJECT SCREENS ---
    # 1. Left Flanking Display: Weather Dashboard (Apple Weather Bento UI)
    draw_bezel_window(
        base_img=img,
        screen_img=img_weather,
        pos=(70, 204),
        size=(610, 343),
        radius=14,
        border_color=(251, 191, 36, 120),
        border_width=2,
        shadow_offset=18,
        shadow_blur=28,
        shadow_alpha=180,
        badge_text="Apple Weather Bento • Doppler Radar & 10-Day",
        badge_pos="bottom_left",
        badge_font=font_pill,
        accent_color=(251, 191, 36)
    )
    
    # 2. Right Flanking Display: Azan Floating Settings (16:9 Modal)
    draw_bezel_window(
        base_img=img,
        screen_img=img_azan,
        pos=(1240, 204),
        size=(610, 343),
        radius=14,
        border_color=(74, 222, 128, 120),
        border_width=2,
        shadow_offset=18,
        shadow_blur=28,
        shadow_alpha=180,
        badge_text="16:9 Azan & Solat Floating Settings",
        badge_pos="bottom_right",
        badge_font=font_pill,
        accent_color=(74, 222, 128)
    )
    
    # 3. Main Center Hero Screen: Home Launcher Screen (Elevated & Prominent)
    draw_bezel_window(
        base_img=img,
        screen_img=img_home,
        pos=(460, 310),
        size=(1000, 562),
        radius=18,
        border_color=(255, 255, 255, 170),
        border_width=3,
        shadow_offset=26,
        shadow_blur=42,
        shadow_alpha=220,
        badge_text="Primary TV Home • Jambatan Angkat Terengganu • Live Dock",
        badge_pos="bottom_right",
        badge_font=font_pill,
        accent_color=(56, 189, 248)
    )
    
    # 4. Bottom-Left Card: StandBy OLED Clock Screensaver
    draw_bezel_window(
        base_img=img,
        screen_img=img_screensaver,
        pos=(70, 640),
        size=(360, 202),
        radius=12,
        border_color=(147, 197, 253, 90),
        border_width=2,
        shadow_offset=14,
        shadow_blur=20,
        shadow_alpha=160,
        badge_text="StandBy OLED Clock",
        badge_pos="bottom_left",
        badge_font=font_pill,
        accent_color=(147, 197, 253)
    )
    
    # 5. Bottom-Right Card: 4K Wallpapers & Phone QR Code Upload
    draw_bezel_window(
        base_img=img,
        screen_img=img_wallpaper,
        pos=(1490, 640),
        size=(360, 202),
        radius=12,
        border_color=(251, 146, 60, 110),
        border_width=2,
        shadow_offset=14,
        shadow_blur=20,
        shadow_alpha=160,
        badge_text="Koleksi Wallpaper 4K & Phone QR",
        badge_pos="bottom_right",
        badge_font=font_pill,
        accent_color=(251, 146, 60)
    )
    
    # --- FOOTER FEATURE BAR (Bottom 915 to 1010) ---
    footer_bar = Image.new('RGBA', (WIDTH - 140, 94), (0, 0, 0, 0))
    fdraw = ImageDraw.Draw(footer_bar)
    fdraw.rounded_rectangle([(0, 0), (WIDTH - 140, 94)], radius=16, fill=(15, 23, 42, 180), outline=(255, 255, 255, 24), width=1)
    
    features = [
        ("WEATHER ENGINE 3.0", "Open-Meteo live sync, APIMS JAS Air Quality, interactive rain radar, hourly pills, dynamic animated skies."),
        ("ISLAMIC LIVING ROOM", "E-Solat Malaysia JAKIM auto zone, 6-phase timetable, 16:9 floating settings dialog with individual prayer toggle."),
        ("4K MALAYSIA & PHONE QR", "Curated 14 Malaysian states, Pexels UHD collections, phone QR photo upload server without external cloud.")
    ]
    
    fx = 32
    for f_title, f_desc in features:
        fdraw.text((fx, 18), f_title, font=font_footer_title, fill=(56, 189, 248, 255))
        # Word wrap description
        words = f_desc.split(' ')
        line1 = ""
        line2 = ""
        for w in words:
            if len(line1 + w) < 42:
                line1 += w + " "
            else:
                line2 += w + " "
        fdraw.text((fx, 40), line1.strip(), font=font_footer_desc, fill=(226, 232, 240, 230))
        if line2:
            fdraw.text((fx, 58), line2.strip(), font=font_footer_desc, fill=(148, 163, 184, 210))
        fx += 580
        
    img.alpha_composite(footer_bar, (70, 915))
    
    # Bottom subtle watermark
    draw.text((70, 1030), "Glass TV OS • Version 3.0.0 Release Architecture • All UI captures taken directly from live Android TV build", font=font_pill, fill=(100, 116, 139, 220))
    draw.text((WIDTH - 380, 1030), "Designed for Xiaomi Mi Box & Android TV 9+", font=font_pill, fill=(100, 116, 139, 220))
    
    # Save as high-quality JPEG
    rgb_img = img.convert('RGB')
    out_path = f"{assets_dir}/glass_tv_os_showcase_poster.jpg"
    rgb_img.save(out_path, format="JPEG", quality=95, optimize=True)
    print(f"Showcase poster generated successfully at {out_path} ({os.path.getsize(out_path)} bytes)")

if __name__ == "__main__":
    main()
