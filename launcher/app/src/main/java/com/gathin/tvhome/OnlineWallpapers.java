package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic 4K dark desktop wallpaper manager.
 * Fetches high-resolution (3840x2160) atmospheric and moody desktop wallpapers
 * directly from an online feed or a curated Unsplash 4K catalog.
 * Caches wallpapers locally for instant loading and offline resilience.
 */
final class OnlineWallpapers {
    private static final String TAG = "TvHome.Online";
    static final String DIR = "online-wallpapers";
    private static final String PREFS_NAME = "online_wallpapers";
    private static final String PREF_LAST_SYNC = "last_sync_time";
    private static final long REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000; // 6 hours
    private static final int MAX_CACHE_FILES = 50;
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024; // 25 MB limit per 4K/8K image

    static class WallpaperItem {
        final String url;
        final String title;
        final String description;
        final String category;

        WallpaperItem(String url, String title, String description, String category) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }

    // Built-in curated catalog of dark, moody, atmospheric 4K desktop wallpapers (Unsplash)
    private static final WallpaperItem[] CURATED_CATALOG = new WallpaperItem[]{
            new WallpaperItem("https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Night Earth Orbit", "Planet Earth illuminated at night from outer space", "Space"),
            new WallpaperItem("https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Milky Way Galaxy", "Deep night sky filled with stellar constellations", "Astronomy"),
            new WallpaperItem("https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Alpine Mountains", "Silhouetted mountain peaks under starry skies", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Tokyo Cyberpunk Alley", "Moody neon reflections in a narrow Tokyo alleyway", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Deep Underwater Abyss", "Sunlight penetrating into the dark deep ocean", "Underwater"),
            new WallpaperItem("https://images.unsplash.com/photo-1531366936337-7c912a4589a7?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Aurora Borealis", "Green atmospheric aurora dancing across arctic skies", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Midnight Ocean Waves", "Deep blue waves crashing on a tranquil dark shore", "Oceans"),
            new WallpaperItem("https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Misty Pine Forest", "Dense evergreen canopy cloaked in rolling fog", "Forest"),
            new WallpaperItem("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Deep Space Nebula", "Cosmic interstellar gas clouds and distant stars", "Cosmos"),
            new WallpaperItem("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Lake Twilight", "Still dark waters reflecting misty mountain ridges", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Desert Dunes", "Shadows sweeping over desert dunes under the night sky", "Desert"),
            new WallpaperItem("https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Cosmic Dark Starfield", "Deep black void speckled with distant galaxies", "Space"),
            new WallpaperItem("https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Neon Rain Street", "Vibrant reflections shimmering on wet asphalt", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1491557345352-5929e343eb89?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Black Mountains", "Dramatic jagged peaks cutting through dark storm clouds", "Mountains"),
            new WallpaperItem("https://images.unsplash.com/photo-1494548162494-384bba4ab999?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Foggy Suspension Bridge", "Golden Gate towers emerging through evening fog", "Architecture"),
            new WallpaperItem("https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Volcanic Landscape", "Black volcanic terrain and quiet minimalist horizon", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Rainy Window Twilight", "Water droplets cascading down glass against city dusk", "Atmospheric"),
            new WallpaperItem("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Ocean Trench", "Bioluminescent glow illuminating the deep ocean", "Underwater"),
            new WallpaperItem("https://images.unsplash.com/photo-1510312305653-8ed496efae75?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Starry Desert Camp", "Distant campfire underneath an endless starlit dome", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Moody Castle Mist", "Ancient stone fortress shrouded in mountain mist", "History"),
            new WallpaperItem("https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Minimal Dark Architecture", "Monolithic skyscraper geometry in evening shadows", "Architecture"),
            new WallpaperItem("https://images.unsplash.com/photo-1483728642387-6c3bdd6c93e5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Midnight Mountain Mirror", "Pristine alpine lake mirroring snowy peaks at night", "Mountains"),
            new WallpaperItem("https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Forest Canopy", "Looking up through towering pines at twilight sky", "Forest"),
            new WallpaperItem("https://images.unsplash.com/photo-1509114397022-ed747cca3f65?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Minimalist Sand Ridges", "Sculpted dark desert dunes in low evening light", "Minimal"),
            new WallpaperItem("https://images.unsplash.com/photo-1426604966848-d7adac402bff?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Mountain Silhouette Dusk", "Gradated sunset sky silhouetting layered ridges", "Sunset"),
            new WallpaperItem("https://images.unsplash.com/photo-1502082553048-f009c37129b9?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Minimalist Forest", "Subtle woodland trees standing in twilight mist", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1505118380757-91f5f5632de0?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Black Sand Beach", "Dark volcanic coast meeting foaming sea waves", "Coastal"),
            new WallpaperItem("https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Neon Reflections", "Cyberpunk metropolis mirrored in rainy streets", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Deep Moody Lake", "Calm alpine water framed by dark pine shadows", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1486870591958-9b9d0d1dda99?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Misty Mountain Ridge", "Rolling mist veiling dramatic jagged cliff faces", "Mountains"),
            new WallpaperItem("https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Forest Road", "Winding roadway disappearing into moody woodland fog", "Forest"),
            new WallpaperItem("https://images.unsplash.com/photo-1534088568595-a066f410bcda?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Moody Thunderstorm Clouds", "Massive dark cumulonimbus clouds gathering at dusk", "Weather"),
            new WallpaperItem("https://images.unsplash.com/photo-1532693322450-2cb5c511067d?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Minimalist Moon Phase", "Crescent moon glowing against a deep pitch-black sky", "Astronomy"),
            new WallpaperItem("https://images.unsplash.com/photo-1498084393753-b411b2d26b34?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Rainy Asphalt", "Glittering light streaks bouncing off wet night streets", "Urban"),
            new WallpaperItem("https://images.unsplash.com/photo-1516214104703-d870798883c5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dark Foggy Pines", "Serene evergreen trees veiled in thick atmospheric fog", "Forest"),
            new WallpaperItem("https://images.unsplash.com/photo-1596422846543-75c6fc197f07?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Petronas Twin Towers Night", "Illuminated skyscraper spires soaring into the Kuala Lumpur night sky", "Skyscraper"),
            new WallpaperItem("https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Manhattan Skyline Twilight", "New York City highrise towers lit against the deep evening dusk", "Skyscraper"),
            new WallpaperItem("https://images.unsplash.com/photo-1477959858617-67f30bc75b82?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Chicago River Skyscrapers", "Glass canyon of modern corporate architecture and night bridges", "Skyscraper"),
            new WallpaperItem("https://images.unsplash.com/photo-1512453979798-5ea266f8880c?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Dubai Marina Highrises", "Futuristic illuminated towers mirrored in the marina waters", "Skyscraper"),
            new WallpaperItem("https://images.unsplash.com/photo-1506973035872-a4ec16b8e8d9?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Hong Kong Cityscape Night", "Dazzling neon metropolis framed by Victoria Harbour", "Skyscraper"),

            // 🇲🇾 A collection for every state in Malaysia
            // Johor
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/2/2f/Pulau_Rawa_2014.jpg",
                    "Pulau Rawa, Mersing", "Stunning white sand beaches and clear blue water in the Mersing islands, Johor", "Malaysia_Johor"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/e/e8/Rawa_Island%2C_Johor%2C_Malaysia.jpg",
                    "Pulau Rawa Coastline", "A calm tropical island shore with natural coral rocks in Johor's waters", "Malaysia_Johor"),

            // Kedah
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/b/b6/Langkawi_Sky_Bridge_%28November_2016%29.jpg",
                    "Langkawi Sky Bridge", "The iconic curved suspension bridge atop Gunung Mat Cincang, over Langkawi's ancient rainforest", "Malaysia_Kedah"),
            new WallpaperItem("https://images.unsplash.com/photo-1590523741831-ab7e8b8f9c7f?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Langkawi, Jewel of Kedah", "Kedah's tropical island jewel, with beautiful beaches and an emerald sea", "Malaysia_Kedah"),

            // Kelantan
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/9/94/Pantai_di_Kota_Bharu%2C_Kelantan.2.jpg",
                    "Pantai Cahaya Bulan & Senok", "A South China Sea shoreline lined with casuarina trees in Kota Bharu", "Malaysia_Kelantan"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/4/49/Pantai_di_Kota_Bharu%2C_Kelantan.3.jpg",
                    "Kelantan Fishing Coast", "A peaceful fishing village and east-coast sea breeze on the Kelantan shore", "Malaysia_Kelantan"),

            // Melaka
            new WallpaperItem("https://images.unsplash.com/photo-1512100356356-de1b84283e18?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Historic Melaka City", "The heritage of the Malay Sultanate and colonial-era landmarks along the Melaka River", "Malaysia_Melaka"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/7/74/Malacca_by_Night_-_panoramio_%281%29.jpg",
                    "Melaka River & Heritage Bridge", "Enchanting night lights along the historic Melaka River promenade", "Malaysia_Melaka"),

            // Negeri Sembilan
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/b/bc/Port-dickson-beach-1.jpg",
                    "Pantai Cahaya Negeri, Port Dickson", "Gentle sandy beaches and coconut palms against the Strait of Malacca in Port Dickson", "Malaysia_NegeriSembilan"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/5/59/Port_Dickson_Beach_next_to_the_Bayu_Beach_Hotel.jpg",
                    "Teluk Kemang Port Dickson", "The calm of Negeri Sembilan's best-known beach resort at dusk", "Malaysia_NegeriSembilan"),

            // Pahang
            new WallpaperItem("https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Cameron Highlands Tea Plantation", "Lush green tea fields and cool views in the Pahang highlands", "Malaysia_Pahang"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/d/d6/Cameron_Bharat_Tea_Plantation%2C_Cameron_Highlands%2C_Malaysia%2C_20250829_1432_4034.jpg",
                    "Bharat Valley, Cameron Highlands", "Cool morning mist over lush green tea hills in Cameron Highlands", "Malaysia_Pahang"),

            // Perak
            new WallpaperItem("https://images.unsplash.com/photo-1532442782935-dc7ee648a2a5?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Ipoh Heritage Town", "Classic tin-mining-era architecture and limestone hills in Ipoh", "Malaysia_Perak"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/6/68/Kellie%27s_Castle.jpg",
                    "Kellie's Castle, Batu Gajah", "A mysterious colonial-era mansion with Moorish architecture in Batu Gajah", "Malaysia_Perak"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/2/23/Ipoh_Kek_Lok_Dong_%288366358711%29.jpg",
                    "Kek Lok Tong Cave Garden", "A grand limestone cave with a calm lake garden among Ipoh's karst hills", "Malaysia_Perak"),

            // Perlis
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/e/e0/Gua_Kelam_-_Perlis%2C_Malaysia.jpg",
                    "Gua Kelam, Kaki Bukit", "An old suspension walkway through a natural limestone cave in Perlis", "Malaysia_Perlis"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/9/97/The_Cave_of_Darkness%2C_Gua_Kelam%2C_Perlis%2C_Malaysia_%284676111629%29.jpg",
                    "Nakawan Range, Gua Kelam", "Limestone geology and an underground stream in Perlis", "Malaysia_Perlis"),

            // Pulau Pinang
            new WallpaperItem("https://images.unsplash.com/photo-1582298538104-fe2e74c27f59?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "George Town UNESCO Heritage", "A UNESCO World Heritage city of historic lanes and colonial architecture", "Malaysia_PulauPinang"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/b/be/Penang_Malaysia_Kek-Lok-Si-Temple-02.jpg",
                    "Kek Lok Si Temple, Air Itam", "Malaysia's largest Buddhist temple, on the Air Itam hillside, with a seven-tier pagoda", "Malaysia_PulauPinang"),

            // Sabah
            new WallpaperItem("https://images.unsplash.com/photo-1571401835393-8c5f35328320?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Mount Kinabalu, Kundasang", "The highest peak in Malaysia, above a stunning sea of clouds", "Malaysia_Sabah"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/b/b7/South_Peak_Mount_Kinabalu_2.jpg",
                    "South Peak, Mount Kinabalu", "Kinabalu's granite peaks silhouetted against the dawn sky, Sabah", "Malaysia_Sabah"),

            // Sarawak
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/5/55/Mulu_Pinnacles.jpg",
                    "Pinnacles, Mulu National Park", "Breathtaking UNESCO-listed limestone spires in the Borneo rainforest of Sarawak", "Malaysia_Sarawak"),
            new WallpaperItem("https://images.unsplash.com/photo-1744525329284-3ffe4bcdf256?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Kuching Waterfront", "The Sarawak River, the Darul Hana Bridge and the iconic Sarawak State Assembly building", "Malaysia_Sarawak"),
            new WallpaperItem("https://images.unsplash.com/photo-1590475461955-3e35807fa3a4?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Miri Coastline", "Sunset over the South China Sea on the Miri coast", "Malaysia_Sarawak"),

            // Selangor
            new WallpaperItem("https://images.unsplash.com/photo-1584824486509-112e4181ff6b?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Batu Caves Sri Subramaniar", "The iconic rainbow staircase against ancient limestone hills and the Lord Murugan statue", "Malaysia_Selangor"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/6/69/Sultan_Salahuddin_Abdul_Aziz_Mosque_by_Fairus_Khafiz.jpg",
                    "Sultan Salahuddin Abdul Aziz Mosque", "Shah Alam's state mosque, with its grand blue dome and Malaysia's tallest minarets", "Malaysia_Selangor"),

            // Terengganu
            new WallpaperItem("https://images.unsplash.com/photo-1544644181-1484b3fdfc62?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Perhentian Islands Marine Paradise", "A marine paradise of clean white sand and living coral reefs", "Malaysia_Terengganu"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/7/7f/Crystal_Mosque_in_2016.jpg",
                    "Crystal Mosque, Kuala Terengganu", "Modern glass-and-crystal architecture floating on the Terengganu River estuary", "Malaysia_Terengganu"),

            // Wilayah Persekutuan
            new WallpaperItem("https://images.unsplash.com/photo-1596422846543-75c6fc197f07?auto=format&fit=crop&w=3840&h=2160&q=80",
                    "Petronas Twin Towers & KLCC", "Malaysia's iconic twin towers in the heart of Kuala Lumpur", "Malaysia_WP"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/f/f5/Petronas_twin_towers_at_night.jpg",
                    "Kuala Lumpur Night Skyline", "Kuala Lumpur at night, glittering with skyscraper lights", "Malaysia_WP")
    };

    private OnlineWallpapers() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, DIR) : null;
    }

    /** Returns all valid downloaded 4K wallpapers in sorted order. */
    static List<File> list(Context ctx) {
        List<File> out = new ArrayList<>();
        File d = dir(ctx);
        File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.length() > 0) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    out.add(f);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    public static boolean matchesCategory(String itemCat, String selectedCat) {
        if (selectedCat == null || selectedCat.isEmpty() || "All".equalsIgnoreCase(selectedCat)) return true;
        if (itemCat == null) return false;
        String ic = itemCat.toLowerCase(Locale.ROOT);
        String sc = selectedCat.toLowerCase(Locale.ROOT);
        if (sc.startsWith("malaysia_")) {
            String state = sc.substring("malaysia_".length());
            return ic.contains(state);
        }
        if (sc.equals("malaysia") || sc.equals("malaysia_all")) {
            return ic.contains("malay");
        }
        if (sc.contains("sky") || sc.contains("arch") || sc.contains("city")) {
            return ic.contains("sky") || ic.contains("arch") || ic.contains("city") || ic.contains("urban");
        }
        if (sc.contains("nat") || sc.contains("land")) {
            return ic.contains("nat") || ic.contains("land") || ic.contains("mount") || ic.contains("forest") || ic.contains("desert");
        }
        if (sc.contains("space") || sc.contains("cosmo") || sc.contains("astro")) {
            return ic.contains("space") || ic.contains("astro") || ic.contains("cosmo");
        }
        if (sc.contains("ocean") || sc.contains("water") || sc.contains("coast")) {
            return ic.contains("ocean") || ic.contains("underwater") || ic.contains("coast");
        }
        if (sc.contains("minim")) {
            return ic.contains("minim") || ic.contains("atmos") || ic.contains("weather");
        }
        if (sc.contains("malay")) {
            return ic.contains("malay");
        }
        return ic.contains(sc);
    }

    static List<File> listByCategory(Context ctx, String category) {
        List<File> all = list(ctx);
        if (category == null || category.equalsIgnoreCase("All")) return all;
        List<File> filtered = new ArrayList<>();
        for (File f : all) {
            String[] desc = describe(f);
            if (desc != null && desc.length >= 3) {
                if (matchesCategory(desc[2], category)) {
                    filtered.add(f);
                }
            }
        }
        return filtered;
    }

    static boolean syncCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        List<WallpaperItem> candidates = new ArrayList<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (matchesCategory(item.category, category)) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) return false;
        Collections.shuffle(candidates);

        boolean anyDownloaded = false;
        int targetNew = 3;
        int count = 0;

        for (WallpaperItem item : candidates) {
            if (count >= targetNew) break;
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue;
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
                    String meta = (item.title != null ? item.title : "4K Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : category);
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }
                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        count++;
                        Log.i(TAG, "Saved category wallpaper [" + category + "]: " + item.title);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }
        return anyDownloaded;
    }

    /** Returns true if sync is needed (empty local pool or refresh interval elapsed). */
    static boolean due(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(PREF_LAST_SYNC, 0);
        return list(ctx).isEmpty() || (System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS);
    }

    /**
     * Reads companion .txt file (same name as picture with .txt extension).
     * Line 1: Title
     * Line 2: Description / Overview
     * Line 3: Category / Source
     */
    static String[] describe(File picture) {
        if (picture == null) return null;
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        File text = new File(picture.getParentFile(), name.substring(0, dot) + ".txt");
        if (!text.isFile() || text.length() == 0 || text.length() > 8192) return null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(text), StandardCharsets.UTF_8))) {
            String title = r.readLine();
            if (title == null || title.trim().isEmpty()) return null;
            String desc = r.readLine();
            String cat = r.readLine();
            return new String[]{
                    title.trim(),
                    desc == null ? "" : desc.trim(),
                    cat == null ? "" : cat.trim()
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Synchronizes wallpapers from an online feed or the built-in curated catalog.
     * Downloads on the caller's thread; call from a background thread.
     * Returns true if one or more fresh pictures were added to the local pool.
     */
    static boolean sync(Context ctx, String feedUrl) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        List<WallpaperItem> items = new ArrayList<>();
        if (feedUrl != null && !feedUrl.trim().isEmpty()) {
            List<WallpaperItem> remote = fetchFeed(feedUrl.trim());
            if (remote != null && !remote.isEmpty()) {
                items.addAll(remote);
                Log.i(TAG, "Loaded " + items.size() + " wallpapers from remote feed: " + feedUrl);
            }
        }

        // If no remote items, use the curated catalog
        if (items.isEmpty()) {
            Collections.addAll(items, CURATED_CATALOG);
        }

        // Shuffle candidate pool so rotation changes over time
        List<WallpaperItem> pool = new ArrayList<>(items);
        Collections.shuffle(pool);

        boolean anyDownloaded = false;
        int targetNew = 5; // Download up to 5 fresh pictures per sync
        int downloadedCount = 0;

        for (WallpaperItem item : pool) {
            if (downloadedCount >= targetNew) break;
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue; // Already cached
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
                    // Write metadata
                    String meta = (item.title != null ? item.title : "4K Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : "Desktop");
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }

                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        downloadedCount++;
                        Log.i(TAG, "Saved 4K wallpaper: " + item.title + " (" + targetFile.length() + " bytes)");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }

        // Prune old files if cache exceeds maximum allowed files
        pruneCache(d);

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                .apply();

        return anyDownloaded;
    }

    private static String hashFilename(String url) {
        return "online_" + Math.abs(url.hashCode());
    }

    private static boolean downloadUrlToFile(String strUrl, File dest) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GlassTV/2.4 (https://github.com/aqmangge/glass-tv-home)");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + code + " for " + strUrl);
                return false;
            }

            in = conn.getInputStream();
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    Log.w(TAG, "Image exceeds byte limit, aborting: " + strUrl);
                    return false;
                }
                out.write(buf, 0, read);
            }
            out.flush();
            return total > 10_000;
        } catch (Exception e) {
            Log.w(TAG, "Download error: " + e.getMessage());
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static List<WallpaperItem> fetchFeed(String feedUrl) {
        List<WallpaperItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(feedUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (GlassTV; Android)");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return list;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString().trim();
            if (raw.startsWith("[")) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof String) {
                        list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                    } else if (o instanceof JSONObject) {
                        JSONObject obj = (JSONObject) o;
                        String u = obj.optString("url");
                        if (!u.isEmpty()) {
                            list.add(new WallpaperItem(
                                    u,
                                    obj.optString("title", "Online Wallpaper"),
                                    obj.optString("description", ""),
                                    obj.optString("category", "Web Server")));
                        }
                    }
                }
            } else if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);
                JSONArray arr = obj.optJSONArray("wallpapers");
                if (arr == null) arr = obj.optJSONArray("images");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.get(i);
                        if (o instanceof String) {
                            list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                        } else if (o instanceof JSONObject) {
                            JSONObject item = (JSONObject) o;
                            String u = item.optString("url");
                            if (!u.isEmpty()) {
                                list.add(new WallpaperItem(
                                        u,
                                        item.optString("title", "Online Wallpaper"),
                                        item.optString("description", ""),
                                        item.optString("category", "Web Server")));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Feed parse error: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static void pruneCache(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= MAX_CACHE_FILES) return;

        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        // Sort oldest first
        Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toRemove = list.size() - MAX_CACHE_FILES;
        for (int i = 0; i < toRemove; i++) {
            File f = list.get(i);
            File txt = new File(dir, f.getName().replace(".jpg", ".txt"));
            f.delete();
            if (txt.exists()) txt.delete();
        }
    }
}
