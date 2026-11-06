package com.example.habitapp.domain.model

/** Groups of suggestions on the "new habit" screen. */
enum class SuggestionCategory(val nameEn: String, val namePt: String, val quit: Boolean = false) {
    FITNESS("Exercise", "Exercícios"),
    HEALTH("Health", "Saúde"),
    MIND("Mind", "Mente"),
    CHORES("Chores", "Tarefas domésticas"),
    REDUCE("Reduce", "Reduzir"),
    QUIT("Quit", "Parar", quit = true),
}

data class HabitSuggestion(
    val id: String,
    val category: SuggestionCategory,
    val nameEn: String,
    val namePt: String,
    val colorKey: String,
    val type: HabitType = HabitType.COUNT,
    val target: Double = 1.0,
    val step: Double = 1.0,
    val unitEn: String = "",
    val unitPt: String = "",
    val goalPeriod: GoalPeriod = GoalPeriod.DAY,
    val goalType: GoalType = GoalType.AT_LEAST,
    val emoji: String = "✅",
) {
    fun name(portuguese: Boolean) = if (portuguese) namePt else nameEn
    fun unit(portuguese: Boolean) = if (portuguese) unitPt else unitEn
}

/** Local, offline catalog of habit ideas. */
object HabitSuggestions {
    private fun s(
        id: String, c: SuggestionCategory, en: String, pt: String, color: String,
        type: HabitType = HabitType.COUNT, target: Double = 1.0, step: Double = 1.0,
        unitEn: String = "", unitPt: String = unitEn, period: GoalPeriod = GoalPeriod.DAY,
        goal: GoalType = GoalType.AT_LEAST, emoji: String = "✅",
    ) = HabitSuggestion(id, c, en, pt, color, type, target, step, unitEn, unitPt, period, goal, emoji)

    val all: List<HabitSuggestion> = listOf(
        s("exercise", SuggestionCategory.FITNESS, "Exercise", "Fazer exercícios", "pink", period = GoalPeriod.WEEK, target = 3.0, emoji = "💪"),
        s("run", SuggestionCategory.FITNESS, "Run", "Correr", "periwinkle", HabitType.DURATION, 30.0, 5.0, emoji = "🏃"),
        s("cardio", SuggestionCategory.FITNESS, "Cardio", "Fazer cardio", "peach", HabitType.DURATION, 20.0, 5.0, emoji = "❤️"),
        s("gym", SuggestionCategory.FITNESS, "Strength training", "Fazer musculação", "teal", period = GoalPeriod.WEEK, target = 5.0, emoji = "🏋️"),
        s("walk", SuggestionCategory.FITNESS, "Walk", "Caminhar", "mint", HabitType.NUMERIC, 5.0, 1.0, "km", "km", emoji = "🚶"),
        s("stretch", SuggestionCategory.FITNESS, "Stretch", "Alongar", "lilac", HabitType.DURATION, 10.0, 5.0, emoji = "🤸"),
        s("pushups", SuggestionCategory.FITNESS, "Push-ups", "Flexões", "coral", target = 30.0, step = 10.0, emoji = "💪"),

        s("water", SuggestionCategory.HEALTH, "Drink water", "Beber água", "lavender", HabitType.NUMERIC, 2.0, 0.25, "liters", "litros", emoji = "💧"),
        s("wake_early", SuggestionCategory.HEALTH, "Wake up early", "Acordar cedo", "yellow", emoji = "⏰"),
        s("sleep_early", SuggestionCategory.HEALTH, "Sleep early", "Dormir cedo", "lilac", emoji = "😴"),
        s("cook", SuggestionCategory.HEALTH, "Cook at home", "Cozinhar", "teal", period = GoalPeriod.WEEK, target = 4.0, emoji = "🍳"),
        s("fruit", SuggestionCategory.HEALTH, "Eat fruit", "Comer frutas", "mint", target = 2.0, emoji = "🍎"),
        s("vitamins", SuggestionCategory.HEALTH, "Take vitamins", "Tomar vitaminas", "amber", emoji = "💊"),

        s("read", SuggestionCategory.MIND, "Read", "Ler", "indigo", HabitType.NUMERIC, 20.0, 5.0, "pages", "páginas", emoji = "📖"),
        s("study", SuggestionCategory.MIND, "Study", "Estudar", "coral", HabitType.DURATION, 60.0, 15.0, emoji = "🧠"),
        s("learn", SuggestionCategory.MIND, "Learn", "Aprender", "mint", HabitType.DURATION, 30.0, 10.0, emoji = "💡"),
        s("meditate", SuggestionCategory.MIND, "Meditate", "Meditar", "peach", HabitType.DURATION, 10.0, 5.0, emoji = "🧘"),
        s("journal", SuggestionCategory.MIND, "Journal", "Escrever no diário", "yellow", emoji = "✍️"),
        s("instrument", SuggestionCategory.MIND, "Practice an instrument", "Praticar um instrumento", "coral", HabitType.DURATION, 15.0, 5.0, emoji = "🎸"),

        s("clean", SuggestionCategory.CHORES, "Clean", "Limpar", "lilac", period = GoalPeriod.WEEK, target = 2.0, emoji = "🧹"),
        s("dishes", SuggestionCategory.CHORES, "Wash the dishes", "Lavar a louça", "silver", emoji = "🍽️"),
        s("laundry", SuggestionCategory.CHORES, "Do laundry", "Lavar roupas", "lavender", period = GoalPeriod.WEEK, target = 1.0, emoji = "🧺"),
        s("vacuum", SuggestionCategory.CHORES, "Vacuum", "Passar aspirador", "slate", period = GoalPeriod.WEEK, target = 1.0, emoji = "🧹"),
        s("plants", SuggestionCategory.CHORES, "Water plants", "Regar plantas", "mint", period = GoalPeriod.WEEK, target = 2.0, emoji = "🌱"),
        s("make_bed", SuggestionCategory.CHORES, "Make the bed", "Arrumar a cama", "peach", emoji = "🛏️"),

        s("smoke_less", SuggestionCategory.REDUCE, "Smoke less", "Fumar menos", "cyan", target = 5.0, unitEn = "cigarettes", unitPt = "cigarros", goal = GoalType.AT_MOST, emoji = "🚬"),
        s("drink_less", SuggestionCategory.REDUCE, "Drink less alcohol", "Beber menos álcool", "purple", period = GoalPeriod.WEEK, target = 3.0, unitEn = "drinks", unitPt = "doses", goal = GoalType.AT_MOST, emoji = "🍷"),
        s("sugar_less", SuggestionCategory.REDUCE, "Eat less sugar", "Comer menos açúcar", "periwinkle", target = 1.0, goal = GoalType.AT_MOST, emoji = "🍬"),
        s("screen_less", SuggestionCategory.REDUCE, "Less screen time", "Menos tempo de tela", "slate", HabitType.DURATION, 120.0, 15.0, goal = GoalType.AT_MOST, emoji = "📱"),
        s("coffee_less", SuggestionCategory.REDUCE, "Less coffee", "Menos café", "amber", target = 2.0, unitEn = "cups", unitPt = "xícaras", goal = GoalType.AT_MOST, emoji = "☕"),

        s("quit_smoking", SuggestionCategory.QUIT, "Quit smoking", "Parar de fumar", "silver", HabitType.QUIT, emoji = "🚭"),
        s("quit_alcohol", SuggestionCategory.QUIT, "Quit alcohol", "Parar de beber álcool", "rose", HabitType.QUIT, emoji = "🍷"),
        s("quit_social", SuggestionCategory.QUIT, "Quit social media", "Parar de usar redes sociais", "sky", HabitType.QUIT, emoji = "📵"),
        s("quit_junk", SuggestionCategory.QUIT, "Quit junk food", "Parar de comer junk food", "peach", HabitType.QUIT, emoji = "🍔"),
        s("quit_nails", SuggestionCategory.QUIT, "Stop biting nails", "Parar de roer unhas", "lilac", HabitType.QUIT, emoji = "💅"),
        s("quit_sugar", SuggestionCategory.QUIT, "Quit sugar", "Parar de comer açúcar", "pink", HabitType.QUIT, emoji = "🍬"),
    )

    fun byId(id: String): HabitSuggestion? = all.firstOrNull { it.id == id }
}
