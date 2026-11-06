#!/usr/bin/env python3
"""Generates the list/group icon set (Material Symbols rounded, Apache-2.0) as vector drawables
plus presentation/components/ListIconCatalog.kt.
Usage: list_icons.py <symbols-rounded-dir> <app/src/main dir>"""
import os, re, sys

src, main = sys.argv[1], sys.argv[2]
# category -> [(key, symbol)]; keys are stored in the database, never rename them.
CATALOG = {
    "general": [("checklist", "playlist_add_check"), ("inbox", "inbox"), ("star", "star"), ("heart", "favorite"),
                ("bookmark", "bookmark"), ("flag", "flag"), ("label", "label"), ("idea", "lightbulb"), ("bolt", "bolt"),
                ("target", "target"), ("pin", "keep"), ("folder", "folder"), ("archive", "archive"), ("box", "inventory_2"),
                ("diamond", "diamond"), ("crown", "crown"), ("trophy", "trophy"), ("medal", "military_tech"), ("key", "key"),
                ("lock", "lock"), ("rocket", "rocket_launch"), ("celebration", "celebration"), ("gift", "redeem"), ("calendar", "calendar_month"),
                ("alarm", "alarm"), ("hourglass", "hourglass_empty"), ("today_icon", "today"), ("notes", "sticky_note_2"), ("puzzle", "extension")],
    "work": [("work", "business_center"), ("laptop", "laptop_mac"), ("code", "code"), ("terminal", "terminal"), ("bug", "bug_report"),
             ("meeting", "groups"), ("mail", "mail"), ("call", "call"), ("chat", "chat"), ("forum", "forum"), ("presentation", "co_present"),
             ("chart", "bar_chart"), ("trending", "trending_up"), ("analytics", "analytics"), ("document", "description"),
             ("print", "print"), ("cloud", "cloud"), ("database", "database"), ("storage", "storage"), ("handshake", "handshake"),
             ("badge", "badge"), ("domain", "domain"), ("engineering", "engineering"), ("support", "support_agent"), ("campaign", "campaign")],
    "study": [("school", "school"), ("book", "menu_book"), ("books", "auto_stories"), ("science", "science"), ("calculate", "calculate"),
              ("translate", "translate"), ("language", "language"), ("history_edu", "history_edu"), ("psychology", "psychology"),
              ("biotech", "biotech"), ("architecture", "architecture"), ("edit", "edit"), ("draw", "draw"), ("quiz", "quiz"),
              ("library", "local_library"), ("public", "public")],
    "home": [("home", "home"), ("cleaning", "cleaning_services"), ("laundry", "local_laundry_service"), ("kitchen", "kitchen"),
             ("bed", "bed"), ("chair", "chair"), ("garden", "yard"), ("plant", "potted_plant"), ("tools", "handyman"), ("build", "build"),
             ("plumbing", "plumbing"), ("light", "emoji_objects"), ("couch", "weekend"), ("bathtub", "bathtub"), ("key_home", "door_front"),
             ("recycle", "recycling"), ("trash", "delete"), ("water", "water_drop"), ("fire", "local_fire_department"), ("thermostat", "thermostat")],
    "health": [("health", "local_hospital"), ("medication", "medication"), ("vaccine", "vaccines"), ("heartbeat", "monitor_heart"),
               ("dentist", "dentistry"), ("eye", "visibility"), ("spa", "spa"), ("meditation", "self_improvement"), ("sleep", "bedtime"),
               ("mood", "mood"), ("weight", "monitor_weight"), ("nutrition", "nutrition"), ("ecg", "ecg_heart"), ("healing", "healing"),
               ("mind", "neurology"), ("pill", "pill")],
    "sport": [("fitness", "fitness_center"), ("run", "directions_run"), ("walk", "directions_walk"), ("bike", "directions_bike"),
              ("swim", "pool"), ("hike", "hiking"), ("yoga", "sports_gymnastics"), ("soccer", "sports_soccer"), ("basketball", "sports_basketball"),
              ("tennis", "sports_tennis"), ("volleyball", "sports_volleyball"), ("martial", "sports_martial_arts"), ("skate", "skateboarding"),
              ("ski", "downhill_skiing"), ("surf", "surfing"), ("golf", "golf_course"), ("timer_sport", "timer"), ("steps", "footprint")],
    "food": [("restaurant", "restaurant"), ("coffee", "coffee"), ("cake", "cake"), ("pizza", "local_pizza"), ("bakery", "bakery_dining"),
             ("lunch", "lunch_dining"), ("ramen", "ramen_dining"), ("icecream", "icecream"), ("bar", "local_bar"), ("wine", "wine_bar"),
             ("liquor", "liquor"), ("grocery", "grocery"), ("egg", "egg"), ("fruit", "nutrition"), ("cooking", "skillet"),
             ("blender", "blender"), ("tea", "emoji_food_beverage"), ("fastfood", "fastfood")],
    "money": [("money", "attach_money"), ("savings", "savings"), ("wallet", "account_balance_wallet"), ("card", "credit_card"),
              ("bank", "account_balance"), ("receipt", "receipt_long"), ("payments", "payments"), ("shopping", "shopping_cart"),
              ("bag", "shopping_bag"), ("store", "storefront"), ("sell", "sell"), ("invest", "candlestick_chart"), ("currency", "currency_exchange"),
              ("pie", "pie_chart")],
    "travel": [("flight", "flight"), ("car", "directions_car"), ("bus", "directions_bus"), ("train", "train"), ("boat", "sailing"),
               ("map", "map"), ("place", "location_on"), ("explore", "explore"), ("luggage", "luggage"), ("hotel", "hotel"),
               ("beach", "beach_access"), ("camping", "camping"), ("mountain", "landscape"), ("city", "location_city"), ("fuel", "local_gas_station"),
               ("parking", "local_parking"), ("motorcycle", "two_wheeler"), ("globe", "travel_explore")],
    "leisure": [("music", "music_note"), ("headphones", "headphones"), ("piano", "piano"), ("guitar", "music_note"), ("movie", "movie"),
                ("tv", "tv"), ("games", "sports_esports"), ("casino", "casino"), ("palette", "palette"), ("brush", "brush"),
                ("camera", "photo_camera"), ("photo", "photo_library"), ("theater", "theater_comedy"), ("mic", "mic"), ("podcast", "podcasts"),
                ("knit", "styler"), ("toys", "toys"), ("park", "park")],
    "people": [("person", "person"), ("groups", "groups"), ("family", "family_restroom"), ("child", "child_care"), ("baby", "stroller"),
               ("elderly", "elderly"), ("friends", "diversity_3"), ("love", "volunteer_activism"), ("pets", "pets"), ("cat", "pets"),
               ("church", "church"), ("community", "diversity_1"), ("cake_party", "cake"), ("face", "face"), ("emoji", "sentiment_satisfied")],
    "nature": [("eco", "eco"), ("sun", "sunny"), ("moon", "dark_mode"), ("cloudy", "partly_cloudy_day"), ("rain", "rainy"),
               ("snow", "ac_unit"), ("flower", "local_florist"), ("forest", "forest"), ("grass", "grass"), ("waves", "waves"),
               ("bug_nature", "pest_control"), ("energy", "energy_savings_leaf")],
    "tech": [("phone", "mobile"), ("watch", "watch"), ("tablet", "tablet_mac"), ("desktop", "desktop_windows"), ("keyboard", "keyboard"),
             ("mouse", "mouse"), ("wifi", "wifi"), ("battery", "battery_full"), ("smart_home", "home_iot_device"), ("robot", "smart_toy"),
             ("camera_video", "videocam"), ("router", "router")],
}

def sym_file(sym):
    for cand in (sym + "-fill", sym):
        p = os.path.join(src, cand + ".svg")
        if os.path.exists(p):
            return cand, p
    return None, None

def drawable(sym, path):
    svg = open(path).read()
    paths = re.findall(r'<path d="([^"]+)"', svg)
    body = "\n".join(f'        <path\n            android:fillColor="#FF000000"\n            android:pathData="{d}" />' for d in paths)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols "{sym}" (Apache License 2.0). Generated by tools/list_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
{body}
    </group>
</vector>
'''

dst = os.path.join(main, "res", "drawable")
seen, missing, cats = set(), [], []
for cat, items in CATALOG.items():
    keys = []
    for key, sym in items:
        assert re.fullmatch(r"[a-z_]+", key), key
        if key in seen:
            continue
        name, path = sym_file(sym)
        if not path:
            missing.append(sym); continue
        seen.add(key); keys.append(key)
        open(os.path.join(dst, f"li_{key}.xml"), "w").write(drawable(name, path))
    cats.append((cat, keys))

kt = ["package com.example.habitapp.presentation.components", "", "import com.example.habitapp.R", "",
      "// Generated by tools/list_icons.py — do not edit by hand.", "",
      "/** Icon categories for lists and groups; keys are what the database stores. */",
      "object ListIconCatalog {",
      "    val categories: List<Pair<String, List<Pair<String, Int>>>> = listOf("]
for cat, keys in cats:
    kt.append(f'        "{cat}" to listOf(')
    for k in keys:
        kt.append(f'            "{k}" to R.drawable.li_{k},')
    kt.append("        ),")
kt += ["    )", "}", ""]
open(os.path.join(main, "java/com/example/habitapp/presentation/components/ListIconCatalog.kt"), "w").write("\n".join(kt))
print("icons:", len(seen), "missing:", missing)
