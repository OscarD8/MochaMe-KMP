import androidx.room.gradle.RoomExtension
extensions.configure<RoomExtension>("room") {
    schemaDirectory("$projectDir/schemas")
}