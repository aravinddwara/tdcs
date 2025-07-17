// use an integer for version numbers
version = 1

cloudstream {
    language = "ta"
    // All of these properties are optional, you can safely remove them

    description = "Tamil TV serials and shows from TamilDhool with Dailymotion support"
    authors = listOf("TamilDhool")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie"
    )

    iconUrl = "https://www.tamildhool.net/wp-content/uploads/2020/08/cropped-tamildhool-favicon-32x32.png"
}
