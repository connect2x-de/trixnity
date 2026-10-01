package de.connect2x.trixnity.clientserverapi.model.user

import de.connect2x.trixnity.core.model.ProfileField

@Deprecated("use Profile from trixnity-core", ReplaceWith("Profile", "de.connect2x.trixnity.core.model.Profile"))
typealias Profile = de.connect2x.trixnity.core.model.Profile

@Deprecated("use Profile from trixnity-core", ReplaceWith("displayName", "de.connect2x.trixnity.core.displayName"))
@Suppress("DEPRECATION")
val Profile.displayName: String?
    get() = get(ProfileField.DisplayName)?.value
@Deprecated("use Profile from trixnity-core", ReplaceWith("avatarUrl", "de.connect2x.trixnity.core.avatarUrl"))
@Suppress("DEPRECATION")
val Profile.avatarUrl: String?
    get() = get(ProfileField.AvatarUrl)?.value
@Deprecated("use Profile from trixnity-core", ReplaceWith("timeZone", "de.connect2x.trixnity.core.timeZone"))
@Suppress("DEPRECATION")
val Profile.timeZone: String?
    get() = get(ProfileField.TimeZone)?.value
