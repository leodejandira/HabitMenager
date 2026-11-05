# Room, Hilt, Navigation and kotlinx.serialization ship their own consumer
# R8 rules, so no project-specific keep rules are needed yet.
# Backup DTOs added in Phase 9 are @Serializable and covered by the
# kotlinx.serialization consumer rules.
