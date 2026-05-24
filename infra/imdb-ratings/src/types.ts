// Wire-compatible with the existing Android contract:
//   core-data/src/main/kotlin/com/omnio/tv/data/remote/api/SeriesGraphApi.kt
//   GET api/shows/{id}/season-ratings -> List<SeriesGraphSeasonRatingsDto>

export interface SeriesGraphEpisodeRatingDto {
  season_number: number;
  episode_number: number;
  vote_average: number;
  name: string | null;
  tconst: string | null;
}

export interface SeriesGraphSeasonRatingsDto {
  episodes: SeriesGraphEpisodeRatingDto[];
}
