package com.college.findit.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed lists of categories, campus locations and the 3 private questions asked for each category. */
public final class QuestionBank {

    private QuestionBank() { }

    public static final List<String> LOCATIONS = List.of(
            "Main Gate", "Library", "Canteen", "Hostel", "Sports Ground", "Auditorium",
            "Admin Block", "CSE / IT Block", "ECE / EEE Block", "Mechanical / Civil Block",
            "Classroom", "Parking Area", "College Bus", "Other");

    private static final Map<String, List<String>> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("Mobile Phone", List.of("What is on the lock screen or wallpaper?",
                "What colour/type of cover or any sticker is on it?", "Any scratch, crack or other mark? Where?"));
        QUESTIONS.put("Laptop / Tablet", List.of("What stickers or marks are on the lid?",
                "What is the desktop wallpaper?", "What is the last 4 characters of the serial number, or the user name?"));
        QUESTIONS.put("Wallet / Purse", List.of("What cards or ID are inside?",
                "Approximately how much cash is inside?", "Any photo, note or special item inside?"));
        QUESTIONS.put("ID Card / Documents", List.of("What name/number is on it?",
                "What is the colour of the lanyard or holder?", "What else was kept with it?"));
        QUESTIONS.put("Bag / Backpack", List.of("What is the brand and colour?",
                "Name two items that were inside.", "Any keychain, tag or damage on it?"));
        QUESTIONS.put("Keys", List.of("How many keys are on the ring?",
                "What keychain or tag is attached?", "What do the keys open (bike, room, locker)?"));
        QUESTIONS.put("Water Bottle / Lunch Box", List.of("What is the colour and brand?",
                "Any sticker, name or dent?", "What was inside, if anything?"));
        QUESTIONS.put("Books / Notebooks", List.of("What is the title or subject?",
                "What name is written inside?", "Any bookmark, loose paper or markings?"));
        QUESTIONS.put("Earphones / Headphones / Charger", List.of("What is the brand and colour?",
                "Any damage, tape or mark on it?", "What case or pouch is it in?"));
        QUESTIONS.put("Watch / Jewellery", List.of("What is the brand or design?",
                "What colour is the strap/metal?", "Any engraving, scratch or special mark?"));
        QUESTIONS.put("Other", List.of("Describe one unique mark on the item.",
                "What colour is it?", "Anything special about it that only the owner would know?"));
    }

    public static Set<String> categories() {
        return QUESTIONS.keySet();
    }

    public static Map<String, List<String>> all() {
        return QUESTIONS;
    }
}
