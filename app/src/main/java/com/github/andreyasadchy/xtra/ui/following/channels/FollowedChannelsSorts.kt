package com.github.andreyasadchy.xtra.ui.following.channels

/**
 * Sort values of the followed channels list, stored in the channel sort table under "followed_channels" (sort in
 * videoSort, order in videoType). Kept from v0.1's FollowedChannelsSortDialog, which U2 replaced with a sheet.
 */
object FollowedChannelsSorts {
    const val SORT_FOLLOWED_AT = "created_at"
    const val SORT_ALPHABETICALLY = "login"
    const val SORT_LAST_BROADCAST = "last_broadcast"

    const val ORDER_ASC = "asc"
    const val ORDER_DESC = "desc"
}
