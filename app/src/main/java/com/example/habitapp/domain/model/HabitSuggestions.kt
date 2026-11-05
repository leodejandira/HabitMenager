package com.example.habitapp.domain.model

import java.time.DayOfWeek

enum class SuggestionCategory(val emoji: String, val nameEn: String, val namePt: String) {
    HEALTH("❤️", "Health", "Saúde"),
    FITNESS("💪", "Fitness", "Exercícios"),
    PRODUCTIVITY("🎯", "Productivity", "Produtividade"),
    LEARNING("📚", "Learning", "Aprendizado"),
    MINDFULNESS("🧘", "Mindfulness", "Atenção plena"),
    PERSONAL_CARE("🧴", "Personal care", "Cuidados pessoais"),
    LIFESTYLE("🌱", "Lifestyle", "Estilo de vida"),
    FINANCE("💰", "Financial discipline", "Disciplina financeira"),
    SOCIAL("👨‍👩‍👧", "Social relationships", "Relacionamentos"),
    QUIT("🚭", "Bad habits to quit", "Maus hábitos para largar"),
}

data class HabitSuggestion(
    val id: String,
    val category: SuggestionCategory,
    val emoji: String,
    val nameEn: String,
    val namePt: String,
    val type: HabitType = HabitType.BOOLEAN,
    val target: Double = 1.0,
    val step: Double = 1.0,
    val unitEn: String = "",
    val unitPt: String = "",
    val colorKey: String = "evergreen",
    val frequency: HabitFrequency = HabitFrequency(),
) {
    fun name(portuguese: Boolean) = if (portuguese) namePt else nameEn
    fun unit(portuguese: Boolean) = if (portuguese) unitPt else unitEn
}

/** Local, offline catalog of habit ideas. */
object HabitSuggestions {
    private val MWF = HabitFrequency(FrequencyType.WEEKDAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
    private fun perWeek(n: Int) = HabitFrequency(FrequencyType.TIMES_PER_WEEK, timesPerPeriod = n)

    val all: List<HabitSuggestion> = listOf(
        HabitSuggestion("drink_water", SuggestionCategory.HEALTH, "💧", "Drink water", "Beber água", HabitType.NUMERIC, 2000.0, 250.0, "ml", "ml", "ocean"),
        HabitSuggestion("vitamins", SuggestionCategory.HEALTH, "💊", "Take vitamins", "Tomar vitaminas", colorKey = "amber"),
        HabitSuggestion("sleep_early", SuggestionCategory.HEALTH, "😴", "Sleep before 11 pm", "Dormir antes das 23h", colorKey = "indigo"),
        HabitSuggestion("eat_fruit", SuggestionCategory.HEALTH, "🍎", "Eat fruit", "Comer frutas", HabitType.COUNT, 3.0, 1.0, "servings", "porções", "coral"),
        HabitSuggestion("veggies", SuggestionCategory.HEALTH, "🥦", "Eat vegetables", "Comer vegetais", colorKey = "olive"),

        HabitSuggestion("walk_steps", SuggestionCategory.FITNESS, "🚶", "Walk", "Caminhar", HabitType.NUMERIC, 5.0, 1.0, "km", "km", "teal"),
        HabitSuggestion("pushups", SuggestionCategory.FITNESS, "💪", "Push-ups", "Flexões", HabitType.COUNT, 30.0, 10.0, "reps", "repetições", "coral"),
        HabitSuggestion("workout", SuggestionCategory.FITNESS, "🏋️", "Work out", "Treinar", colorKey = "rose", frequency = MWF),
        HabitSuggestion("run", SuggestionCategory.FITNESS, "🏃", "Go for a run", "Correr", HabitType.DURATION, 30.0, 5.0, colorKey = "ocean", frequency = perWeek(3)),
        HabitSuggestion("stretch", SuggestionCategory.FITNESS, "🤸", "Stretch", "Alongar", HabitType.DURATION, 10.0, 5.0, colorKey = "violet"),

        HabitSuggestion("plan_day", SuggestionCategory.PRODUCTIVITY, "🗓️", "Plan the day", "Planejar o dia", colorKey = "indigo"),
        HabitSuggestion("deep_work", SuggestionCategory.PRODUCTIVITY, "🎯", "Deep work", "Trabalho focado", HabitType.DURATION, 90.0, 15.0, colorKey = "slate"),
        HabitSuggestion("inbox_zero", SuggestionCategory.PRODUCTIVITY, "📥", "Clear inbox", "Zerar a caixa de entrada", colorKey = "ocean"),
        HabitSuggestion("tidy_desk", SuggestionCategory.PRODUCTIVITY, "🧹", "Tidy workspace", "Organizar a mesa", colorKey = "amber"),

        HabitSuggestion("read_pages", SuggestionCategory.LEARNING, "📖", "Read", "Ler", HabitType.COUNT, 20.0, 5.0, "pages", "páginas", "violet"),
        HabitSuggestion("study", SuggestionCategory.LEARNING, "🧠", "Study", "Estudar", HabitType.DURATION, 60.0, 15.0, colorKey = "indigo", frequency = perWeek(5)),
        HabitSuggestion("language", SuggestionCategory.LEARNING, "🗣️", "Practice a language", "Praticar um idioma", HabitType.DURATION, 15.0, 5.0, colorKey = "teal"),
        HabitSuggestion("instrument", SuggestionCategory.LEARNING, "🎸", "Practice an instrument", "Praticar um instrumento", HabitType.DURATION, 20.0, 5.0, colorKey = "coral"),

        HabitSuggestion("meditate", SuggestionCategory.MINDFULNESS, "🧘", "Meditate", "Meditar", HabitType.DURATION, 10.0, 5.0, colorKey = "evergreen"),
        HabitSuggestion("gratitude", SuggestionCategory.MINDFULNESS, "🙏", "Write 3 gratitudes", "Escrever 3 gratidões", colorKey = "amber"),
        HabitSuggestion("journal", SuggestionCategory.MINDFULNESS, "✍️", "Journal", "Escrever no diário", colorKey = "violet"),
        HabitSuggestion("breathe", SuggestionCategory.MINDFULNESS, "🌬️", "Breathing exercise", "Exercício de respiração", colorKey = "ocean"),

        HabitSuggestion("floss", SuggestionCategory.PERSONAL_CARE, "🦷", "Floss", "Usar fio dental", colorKey = "teal"),
        HabitSuggestion("skincare", SuggestionCategory.PERSONAL_CARE, "🧴", "Skincare routine", "Rotina de skincare", colorKey = "rose"),
        HabitSuggestion("sunscreen", SuggestionCategory.PERSONAL_CARE, "☀️", "Apply sunscreen", "Passar protetor solar", colorKey = "amber"),

        HabitSuggestion("make_bed", SuggestionCategory.LIFESTYLE, "🛏️", "Make the bed", "Arrumar a cama", colorKey = "slate"),
        HabitSuggestion("cook", SuggestionCategory.LIFESTYLE, "🍳", "Cook at home", "Cozinhar em casa", colorKey = "coral", frequency = perWeek(4)),
        HabitSuggestion("outdoors", SuggestionCategory.LIFESTYLE, "🌳", "Time outdoors", "Tempo ao ar livre", HabitType.DURATION, 30.0, 10.0, colorKey = "olive"),
        HabitSuggestion("plants", SuggestionCategory.LIFESTYLE, "🌱", "Water the plants", "Regar as plantas", colorKey = "evergreen", frequency = perWeek(2)),

        HabitSuggestion("track_spending", SuggestionCategory.FINANCE, "📒", "Track expenses", "Anotar gastos", colorKey = "olive"),
        HabitSuggestion("save_money", SuggestionCategory.FINANCE, "💰", "Save money", "Guardar dinheiro", HabitType.NUMERIC, 10.0, 5.0, "R$", "R$", "amber"),
        HabitSuggestion("no_spend", SuggestionCategory.FINANCE, "🚫", "No-spend day", "Dia sem gastos", colorKey = "slate", frequency = perWeek(2)),

        HabitSuggestion("call_family", SuggestionCategory.SOCIAL, "📞", "Call family", "Ligar para a família", colorKey = "rose", frequency = perWeek(2)),
        HabitSuggestion("message_friend", SuggestionCategory.SOCIAL, "💬", "Message a friend", "Mandar mensagem a um amigo", colorKey = "ocean"),
        HabitSuggestion("quality_time", SuggestionCategory.SOCIAL, "❤️", "Quality time together", "Tempo de qualidade juntos", colorKey = "coral"),

        HabitSuggestion("quit_smoking", SuggestionCategory.QUIT, "🚭", "Quit smoking", "Parar de fumar", HabitType.QUIT, colorKey = "slate"),
        HabitSuggestion("quit_drinking", SuggestionCategory.QUIT, "🍷", "Quit drinking", "Parar de beber", HabitType.QUIT, colorKey = "rose"),
        HabitSuggestion("quit_social", SuggestionCategory.QUIT, "📵", "Less social media", "Menos redes sociais", HabitType.QUIT, colorKey = "indigo"),
        HabitSuggestion("quit_junk", SuggestionCategory.QUIT, "🍔", "No junk food", "Sem junk food", HabitType.QUIT, colorKey = "amber"),
        HabitSuggestion("quit_nails", SuggestionCategory.QUIT, "💅", "Stop biting nails", "Parar de roer unhas", HabitType.QUIT, colorKey = "violet"),
    )

    fun byId(id: String): HabitSuggestion? = all.firstOrNull { it.id == id }
}
