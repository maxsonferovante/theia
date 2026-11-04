import SwiftUI

struct TheiaOnboardingView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let onContinue: () -> Void

    var body: some View {
        ZStack {
            TheiaSettingsPalette.deepSky.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 18) {
                Text("Θ").font(.system(size: 54, weight: .light, design: .serif)).foregroundStyle(TheiaSettingsPalette.gold).accessibilityHidden(true)
                Text("onboarding_title").font(.largeTitle.weight(.semibold)).foregroundStyle(TheiaSettingsPalette.ivory).accessibilityAddTraits(.isHeader)
                Text("onboarding_intro").font(.body).foregroundStyle(TheiaSettingsPalette.stone)
                OnboardingRow(number: "1", title: "onboarding_step_one_title", detail: "onboarding_step_one_body")
                OnboardingRow(number: "2", title: "onboarding_step_two_title", detail: "onboarding_step_two_body")
                OnboardingRow(number: "3", title: "onboarding_step_three_title", detail: "onboarding_step_three_body")
                Button(action: onContinue) {
                    Text("onboarding_get_started")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(TheiaSettingsPalette.deepSky)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(TheiaSettingsPalette.gold, in: Capsule())
                }
                .frame(minHeight: 54)
                .accessibilityLabel(Text("onboarding_get_started"))
                .accessibilityHint(Text("onboarding_permissions_note"))
                Button("onboarding_skip", action: onContinue)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundStyle(TheiaSettingsPalette.ivory)
                    .accessibilityHint(Text("onboarding_skip_hint"))
                Text("onboarding_permissions_note").font(.caption).foregroundStyle(TheiaSettingsPalette.stone).multilineTextAlignment(.center)
            }
            .padding(24)
            .frame(maxWidth: 560)
        }
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
    }
}

private struct OnboardingRow: View {
    let number: String
    let title: LocalizedStringKey
    let detail: LocalizedStringKey

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Text(number).font(.title3.weight(.semibold)).foregroundStyle(TheiaSettingsPalette.gold)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline).foregroundStyle(TheiaSettingsPalette.ivory)
                Text(detail).font(.subheadline).foregroundStyle(TheiaSettingsPalette.stone)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(TheiaSettingsPalette.cosmicViolet, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }
}

struct TheiaSettingsView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("instant_development") private var instantDevelopment = false
    @AppStorage("geotagging_enabled") private var geotaggingEnabled = false
    @AppStorage("shutter_sound_enabled") private var shutterSoundEnabled = true
    @AppStorage("language_tag") private var languageTag = Locale.current.language.languageCode?.identifier == "pt" ? "pt-BR" : "en"

    var body: some View {
        Form {
            Section("settings_preferences") {
                Toggle("settings_instant_development", isOn: $instantDevelopment)
                    .accessibilityHint(Text("settings_instant_development_description"))
                Toggle("settings_geotagging", isOn: $geotaggingEnabled)
                    .accessibilityHint(Text("settings_geotagging_description"))
                Toggle("settings_shutter_sound", isOn: $shutterSoundEnabled)
                    .accessibilityHint(Text("settings_shutter_sound_description"))
            }
            Section("settings_language") {
                Picker("settings_language", selection: $languageTag) {
                    Text("settings_language_portuguese").tag("pt-BR")
                    Text("settings_language_english").tag("en")
                }
                .pickerStyle(.segmented)
            }
            Section("settings_permissions") {
                Text("settings_permission_privacy_note")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text("settings_camera_permission_description")
                Text("settings_photos_permission_description")
                Text("settings_microphone_permission_description")
                Text("settings_location_permission_description")
            }
        }
        .scrollContentBackground(.hidden)
        .background(TheiaSettingsPalette.deepSky)
        .foregroundStyle(TheiaSettingsPalette.ivory)
        .navigationTitle("settings_title")
        .environment(\.locale, Locale(identifier: languageTag))
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
    }
}

private enum TheiaSettingsPalette {
    static let gold = Color(red: 234 / 255, green: 174 / 255, blue: 53 / 255)
    static let ivory = Color(red: 231 / 255, green: 215 / 255, blue: 183 / 255)
    static let deepSky = Color(red: 24 / 255, green: 35 / 255, blue: 68 / 255)
    static let cosmicViolet = Color(red: 45 / 255, green: 45 / 255, blue: 87 / 255)
    static let stone = Color(red: 182 / 255, green: 165 / 255, blue: 140 / 255)
}
