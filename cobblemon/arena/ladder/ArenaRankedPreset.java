package cobblemon.arena.ladder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum ArenaRankedPreset {
    CUSTOM(
        "custom",
        "Personalizado",
        "Ladder Personalizada",
        "Regras de ladder editáveis.",
        "singles",
        50,
        false,
        true,
        true,
        List.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of()
    ),
    OVER_USED(
        "over_used",
        "OU",
        "OU",
        "Regras oficiais de OU Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of(
            "Standard",
            "Evasion Abilities Clause",
            "Sleep Moves Clause",
            "!Sleep Clause Mod",
            "-Uber",
            "-AG",
            "-Arena Trap",
            "-Moody",
            "-Shadow Tag",
            "-King's Rock",
            "-Razor Fang",
            "-Baton Pass",
            "-Last Respects",
            "-Shed Tail"
        ),
        Set.of(),
        Set.of("uber", "ag"),
        Set.of("sandveil", "snowcloak", "arenatrap", "moody", "shadowtag"),
        Set.of("kingsrock", "razorfang"),
        Set.of(
            "batonpass",
            "darkvoid",
            "grasswhistle",
            "hypnosis",
            "lastrespects",
            "lovelykiss",
            "shedtail",
            "sing",
            "sleeppowder",
            "spore",
            "yawn"
        )
    ),
    UBERS(
        "ubers",
        "Ubers",
        "Ubers",
        "Regras oficiais de Ubers Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of(
            "Standard",
            "-AG",
            "-Moody",
            "-King's Rock",
            "-Razor Fang",
            "-Baton Pass",
            "-Last Respects"
        ),
        Set.of(),
        Set.of("ag"),
        Set.of("moody"),
        Set.of("kingsrock", "razorfang"),
        Set.of("batonpass", "lastrespects")
    ),
    UNDER_USED(
        "under_used",
        "UU",
        "UU",
        "Regras oficiais de UU Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of("[Gen 9] OU", "-OU", "-UUBL"),
        Set.of(),
        Set.of("ou", "uubl"),
        Set.of(),
        Set.of(),
        Set.of()
    ),
    RARELY_USED(
        "rarely_used",
        "RU",
        "RU",
        "Regras oficiais de RU Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of("[Gen 9] UU", "-UU", "-RUBL", "-Light Clay"),
        Set.of(),
        Set.of("uu", "rubl"),
        Set.of(),
        Set.of("lightclay"),
        Set.of()
    ),
    NEVER_USED(
        "never_used",
        "NU",
        "NU",
        "Regras oficiais de NU Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of("[Gen 9] RU", "-RU", "-NUBL", "-Drought", "-Quick Claw"),
        Set.of(),
        Set.of("ru", "nubl"),
        Set.of("drought"),
        Set.of("quickclaw"),
        Set.of()
    ),
    PU(
        "pu",
        "PU",
        "PU",
        "Regras oficiais de PU Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of("[Gen 9] NU", "-NU", "-PUBL", "-Damp Rock"),
        Set.of(),
        Set.of("nu", "publ"),
        Set.of(),
        Set.of("damprock"),
        Set.of()
    ),
    LITTLE_CUP(
        "little_cup",
        "LC",
        "LC",
        "Regras oficiais de Little Cup Singles da Gen 9.",
        "singles",
        5,
        true,
        true,
        false,
        List.of(
            "Little Cup",
            "Standard",
            "-Aipom",
            "-Basculin-White-Striped",
            "-Cutiefly",
            "-Diglett-Base",
            "-Dunsparce",
            "-Duraludon",
            "-Flittle",
            "-Gastly",
            "-Girafarig",
            "-Gligar",
            "-Magby",
            "-Meditite",
            "-Misdreavus",
            "-Murkrow",
            "-Porygon",
            "-Qwilfish-Hisui",
            "-Rufflet",
            "-Scraggy",
            "-Scyther",
            "-Shellder",
            "-Sneasel",
            "-Sneasel-Hisui",
            "-Snivy",
            "-Stantler",
            "-Torchic",
            "-Voltorb-Hisui",
            "-Vulpix",
            "-Vulpix-Alola",
            "-Yanma",
            "-Moody",
            "-Heat Rock",
            "-Baton Pass",
            "-Sticky Web"
        ),
        Set.of(
            "Aipom",
            "Basculin-White-Striped",
            "Cutiefly",
            "Diglett",
            "Dunsparce",
            "Duraludon",
            "Flittle",
            "Gastly",
            "Girafarig",
            "Gligar",
            "Magby",
            "Meditite",
            "Misdreavus",
            "Murkrow",
            "Porygon",
            "Qwilfish-Hisui",
            "Rufflet",
            "Scraggy",
            "Scyther",
            "Shellder",
            "Sneasel",
            "Sneasel-Hisui",
            "Snivy",
            "Stantler",
            "Torchic",
            "Voltorb-Hisui",
            "Vulpix",
            "Vulpix-Alola",
            "Yanma"
        ),
        Set.of(),
        Set.of("moody"),
        Set.of("heatrock"),
        Set.of("batonpass", "stickyweb")
    ),
    MONOTYPE(
        "monotype",
        "Monotype",
        "Monotype",
        "Regras oficiais de Monotype Singles da Gen 9.",
        "singles",
        100,
        true,
        true,
        false,
        List.of(
            "Standard",
            "Evasion Abilities Clause",
            "Same Type Clause",
            "Terastal Clause",
            "-Annihilape",
            "-Arceus",
            "-Baxcalibur",
            "-Calyrex-Ice",
            "-Calyrex-Shadow",
            "-Chi-Yu",
            "-Chien-Pao",
            "-Blaziken",
            "-Deoxys-Normal",
            "-Deoxys-Attack",
            "-Dialga",
            "-Dialga-Origin",
            "-Espathra",
            "-Eternatus",
            "-Giratina",
            "-Giratina-Origin",
            "-Gouging Fire",
            "-Groudon",
            "-Ho-Oh",
            "-Iron Bundle",
            "-Kingambit",
            "-Koraidon",
            "-Kyogre",
            "-Kyurem-Black",
            "-Kyurem-White",
            "-Lugia",
            "-Lunala",
            "-Magearna",
            "-Mewtwo",
            "-Miraidon",
            "-Necrozma-Dawn-Wings",
            "-Necrozma-Dusk-Mane",
            "-Palafin",
            "-Palkia",
            "-Palkia-Origin",
            "-Rayquaza",
            "-Reshiram",
            "-Shaymin-Sky",
            "-Solgaleo",
            "-Ursaluna-Bloodmoon",
            "-Urshifu-Single-Strike",
            "-Zacian",
            "-Zacian-Crowned",
            "-Zamazenta",
            "-Zamazenta-Crowned",
            "-Zekrom",
            "-Moody",
            "-Shadow Tag",
            "-Booster Energy",
            "-Damp Rock",
            "-Focus Band",
            "-King's Rock",
            "-Quick Claw",
            "-Razor Fang",
            "-Smooth Rock",
            "-Baton Pass",
            "-Last Respects",
            "-Shed Tail"
        ),
        Set.of(
            "Annihilape",
            "Arceus",
            "Baxcalibur",
            "Calyrex-Ice",
            "Calyrex-Shadow",
            "Chi-Yu",
            "Chien-Pao",
            "Blaziken",
            "Deoxys-Normal",
            "Deoxys-Attack",
            "Dialga",
            "Dialga-Origin",
            "Espathra",
            "Eternatus",
            "Giratina",
            "Giratina-Origin",
            "Gouging Fire",
            "Groudon",
            "Ho-Oh",
            "Iron Bundle",
            "Kingambit",
            "Koraidon",
            "Kyogre",
            "Kyurem-Black",
            "Kyurem-White",
            "Lugia",
            "Lunala",
            "Magearna",
            "Mewtwo",
            "Miraidon",
            "Necrozma-Dawn-Wings",
            "Necrozma-Dusk-Mane",
            "Palafin",
            "Palkia",
            "Palkia-Origin",
            "Rayquaza",
            "Reshiram",
            "Shaymin-Sky",
            "Solgaleo",
            "Ursaluna-Bloodmoon",
            "Urshifu-Single-Strike",
            "Zacian",
            "Zacian-Crowned",
            "Zamazenta",
            "Zamazenta-Crowned",
            "Zekrom"
        ),
        Set.of(),
        Set.of("moody", "shadowtag"),
        Set.of(
            "boosterenergy",
            "damprock",
            "focusband",
            "kingsrock",
            "quickclaw",
            "razorfang",
            "smoothrock"
        ),
        Set.of("batonpass", "lastrespects", "shedtail")
    );

    private final String key;
    private final String selectionLabel;
    private final String displayName;
    private final String description;
    private final String battleTypeId;
    private final int adjustLevel;
    private final boolean allowRestrictedPokemon;
    private final boolean enforceSpeciesClause;
    private final boolean enforceItemClause;
    private final List<String> showdownRules;
    private final Set<String> bannedSpecies;
    private final Set<String> bannedTiers;
    private final Set<String> bannedAbilities;
    private final Set<String> bannedItems;
    private final Set<String> bannedMoves;
    private static final Set<String> AG_SPECIES = Set.of(
        "calyrexshadow",
        "miraidon"
    );
    private static final Set<String> UBER_SPECIES = Set.of(
        "mewtwo",
        "sneasler",
        "ursalunabloodmoon",
        "lugia",
        "hooh",
        "kyogre",
        "groudon",
        "rayquaza",
        "deoxys",
        "deoxysattack",
        "dialga",
        "dialgaorigin",
        "palkia",
        "palkiaorigin",
        "giratina",
        "giratinaorigin",
        "shayminsky",
        "arceus",
        "volcarona",
        "reshiram",
        "zekrom",
        "landorus",
        "kyuremblack",
        "kyuremwhite",
        "solgaleo",
        "lunala",
        "necrozmaduskmane",
        "necrozmadawnwings",
        "magearna",
        "zacian",
        "zaciancrowned",
        "zamazentacrowned",
        "eternatus",
        "urshifu",
        "urshifurapidstrike",
        "regieleki",
        "spectrier",
        "calyrexice",
        "espathra",
        "palafin",
        "baxcalibur",
        "fluttermane",
        "roaringmoon",
        "ironbundle",
        "chienpao",
        "chiyu",
        "koraidon",
        "annihilape",
        "ogerponhearthflame",
        "archaludon",
        "gougingfire",
        "terapagos",
        "terapagosstellar"
    );
    private static final Set<String> OU_SPECIES = Set.of(
        "clefable",
        "slowkinggalar",
        "weezinggalar",
        "zapdos",
        "moltres",
        "dragonite",
        "gliscor",
        "tyranitar",
        "deoxysspeed",
        "heatran",
        "darkrai",
        "samurotthisui",
        "alomomola",
        "tornadustherian",
        "landorustherian",
        "kyurem",
        "primarina",
        "rillaboom",
        "cinderace",
        "corviknight",
        "hatterene",
        "dragapult",
        "zamazenta",
        "enamorus",
        "dondozo",
        "garganacl",
        "glimmora",
        "gholdengo",
        "greattusk",
        "irontreads",
        "ironmoth",
        "ironvaliant",
        "tinglu",
        "ceruledge",
        "kingambit",
        "walkingwake",
        "ogerponwellspring",
        "ragingbolt",
        "ironcrown",
        "pecharunt"
    );
    private static final Set<String> UUBL_SPECIES = Set.of(
        "moltresgalar",
        "ursaluna",
        "blaziken",
        "pelipper",
        "latias",
        "garchomp",
        "hoopaunbound",
        "kommoo",
        "polteageist",
        "zarude",
        "meowscarada",
        "quaquaval",
        "ironhands",
        "okidogi",
        "ogerponcornerstone",
        "ironboulder"
    );
    private static final Set<String> UU_SPECIES = Set.of(
        "arcaninehisui",
        "slowking",
        "scizor",
        "zapdosgalar",
        "azumarill",
        "weavile",
        "skarmory",
        "donphan",
        "metagross",
        "latios",
        "hippowdon",
        "rotomwash",
        "manaphy",
        "excadrill",
        "conkeldurr",
        "mandibuzz",
        "cobalion",
        "thundurustherian",
        "keldeo",
        "greninja",
        "toxapex",
        "skeledirge",
        "lokix",
        "revavroom",
        "sandyshocks",
        "slitherwing",
        "ironjugulis",
        "tinkaton",
        "clodsire",
        "sinistcha",
        "fezandipiti",
        "ogerpon",
        "hydrapple"
    );
    private static final Set<String> RUBL_SPECIES = Set.of(
        "blastoise",
        "gyarados",
        "yanmega",
        "mamoswine",
        "salamence",
        "serperior",
        "lilliganthisui",
        "zoroarkhisui",
        "haxorus",
        "hydreigon",
        "thundurus",
        "hawlucha",
        "volcanion",
        "oricoriopompom",
        "comfey",
        "enamorustherian",
        "ironleaves"
    );
    private static final Set<String> RU_SPECIES = Set.of(
        "politoed",
        "slowbro",
        "magnezone",
        "mukalola",
        "gengar",
        "blissey",
        "kleavor",
        "umbreon",
        "porygonz",
        "mew",
        "quagsire",
        "forretress",
        "entei",
        "suicune",
        "gardevoir",
        "gallade",
        "breloom",
        "crawdaunt",
        "registeel",
        "jirachi",
        "torterra",
        "empoleon",
        "gastrodon",
        "basculegionf",
        "krookodile",
        "mienshao",
        "bisharp",
        "chesnaught",
        "talonflame",
        "goodrahisui",
        "noivern",
        "diancie",
        "ribombee",
        "lycanrocdusk",
        "mimikyu",
        "maushold",
        "cyclizar",
        "armarouge"
    );
    private static final Set<String> NUBL_SPECIES = Set.of(
        "cloyster",
        "feraligatr",
        "deoxysdefense",
        "lucario",
        "azelf",
        "cresselia",
        "terrakion",
        "oricoriosensu",
        "necrozma",
        "regidrago",
        "cetitan",
        "ironthorns"
    );
    private static final Set<String> NU_SPECIES = Set.of(
        "vileplume",
        "tentacruel",
        "slowbrogalar",
        "rhyperior",
        "chansey",
        "scyther",
        "taurospaldeaaqua",
        "vaporeon",
        "espeon",
        "sylveon",
        "articunogalar",
        "dudunsparce",
        "gligar",
        "overqwil",
        "raikou",
        "swampert",
        "flygon",
        "altaria",
        "infernape",
        "staraptor",
        "bronzong",
        "basculegion",
        "scrafty",
        "cinccino",
        "reuniclus",
        "chandelure",
        "braviary",
        "tornadus",
        "meloetta",
        "goodra",
        "klefki",
        "avalugg",
        "decidueye",
        "incineroar",
        "araquanid",
        "tsareena",
        "thwackey",
        "barraskewda",
        "toxtricity",
        "copperajah",
        "duraludon",
        "houndstone",
        "bellibolt",
        "kilowattrel",
        "flamigo",
        "grafaiai",
        "brambleghast",
        "screamtail",
        "wochien",
        "munkidori"
    );
    private static final Set<String> PUBL_SPECIES = Set.of(
        "heracross",
        "dragalge",
        "inteleon",
        "drednaw",
        "frosmoth",
        "indeedee"
    );

    private ArenaRankedPreset(
        String key,
        String selectionLabel,
        String displayName,
        String description,
        String battleTypeId,
        int adjustLevel,
        boolean allowRestrictedPokemon,
        boolean enforceSpeciesClause,
        boolean enforceItemClause,
        List<String> showdownRules,
        Set<String> bannedSpecies,
        Set<String> bannedTiers,
        Set<String> bannedAbilities,
        Set<String> bannedItems,
        Set<String> bannedMoves
    ) {
        this.key = key;
        this.selectionLabel = selectionLabel;
        this.displayName = displayName;
        this.description = description;
        this.battleTypeId = battleTypeId;
        this.adjustLevel = adjustLevel;
        this.allowRestrictedPokemon = allowRestrictedPokemon;
        this.enforceSpeciesClause = enforceSpeciesClause;
        this.enforceItemClause = enforceItemClause;
        this.showdownRules = List.copyOf(showdownRules);
        this.bannedSpecies = Set.copyOf(bannedSpecies);
        this.bannedTiers = Set.copyOf(bannedTiers);
        this.bannedAbilities = Set.copyOf(bannedAbilities);
        this.bannedItems = Set.copyOf(bannedItems);
        this.bannedMoves = Set.copyOf(bannedMoves);
    }

    public String getKey() {
        return this.key;
    }

    public String getSelectionLabel() {
        return this.selectionLabel;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }

    public String getBattleTypeId() {
        return this.battleTypeId;
    }

    public int getAdjustLevel() {
        return this.adjustLevel;
    }

    public boolean allowsRestrictedPokemon() {
        return this.allowRestrictedPokemon;
    }

    public boolean enforcesSpeciesClause() {
        return this.enforceSpeciesClause;
    }

    public boolean enforcesItemClause() {
        return this.enforceItemClause;
    }

    public List<String> getShowdownRules() {
        return this.showdownRules;
    }

    public Set<String> getBannedSpecies() {
        return this.bannedSpecies;
    }

    public Set<String> getBannedTiers() {
        return this.bannedTiers;
    }

    public Set<String> getBannedAbilities() {
        return this.bannedAbilities;
    }

    public Set<String> getBannedItems() {
        return this.bannedItems;
    }

    public Set<String> getBannedMoves() {
        return this.bannedMoves;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public boolean isLockedPreset() {
        return this != CUSTOM;
    }

    public static ArenaRankedPreset fromKey(String key) {
        String normalized = normalizeKey(key);

        normalized = switch (normalized) {
            case "ou" -> "over_used";
            case "uu" -> "under_used";
            case "ru" -> "rarely_used";
            case "nu" -> "never_used";
            case "lc" -> "little_cup";
            default -> normalized;
        };

        for (ArenaRankedPreset preset : values()) {
            if (preset.key.equals(normalized)) {
                return preset;
            }
        }

        return CUSTOM;
    }

    public static List<String> selectionOptions() {
        return List.of(
            CUSTOM.selectionLabel,
            OVER_USED.selectionLabel,
            UBERS.selectionLabel,
            UNDER_USED.selectionLabel,
            RARELY_USED.selectionLabel,
            NEVER_USED.selectionLabel,
            PU.selectionLabel,
            LITTLE_CUP.selectionLabel,
            MONOTYPE.selectionLabel
        );
    }

    public static String selectionForKey(String key) {
        return fromKey(key).selectionLabel;
    }

    public static String keyForSelection(String selection) {
        if (selection != null && !selection.isBlank()) {
            String normalized = selection.trim().toLowerCase(Locale.ROOT);

            for (ArenaRankedPreset preset : values()) {
                if (
                    preset.selectionLabel
                        .toLowerCase(Locale.ROOT)
                        .equals(normalized)
                ) {
                    return preset.key;
                }
            }

            return CUSTOM.key;
        } else {
            return CUSTOM.key;
        }
    }

    public static boolean matchesTier(String speciesId, String tierKey) {
        String normalizedSpecies = ArenaLadder.normalizeRuleKey(speciesId);
        String var3 = normalizeKey(tierKey);

        return switch (var3) {
            case "ag" -> AG_SPECIES.contains(normalizedSpecies);
            case "uber" -> UBER_SPECIES.contains(normalizedSpecies);
            case "ou" -> OU_SPECIES.contains(normalizedSpecies);
            case "uubl" -> UUBL_SPECIES.contains(normalizedSpecies);
            case "uu" -> UU_SPECIES.contains(normalizedSpecies);
            case "rubl" -> RUBL_SPECIES.contains(normalizedSpecies);
            case "ru" -> RU_SPECIES.contains(normalizedSpecies);
            case "nubl" -> NUBL_SPECIES.contains(normalizedSpecies);
            case "nu" -> NU_SPECIES.contains(normalizedSpecies);
            case "publ" -> PUBL_SPECIES.contains(normalizedSpecies);
            default -> false;
        };
    }

    public static String formatTierName(String tierKey) {
        String var1 = normalizeKey(tierKey);

        return switch (var1) {
            case "ag" -> "AG";
            case "uber" -> "Uber";
            case "ou" -> "OU";
            case "uubl" -> "UUBL";
            case "uu" -> "UU";
            case "rubl" -> "RUBL";
            case "ru" -> "RU";
            case "nubl" -> "NUBL";
            case "nu" -> "NU";
            case "publ" -> "PUBL";
            default -> tierKey == null ? "" : tierKey.trim();
        };
    }

    private static String normalizeKey(String value) {
        return value != null && !value.isBlank()
            ? value.trim().toLowerCase(Locale.ROOT)
            : "custom";
    }
}
