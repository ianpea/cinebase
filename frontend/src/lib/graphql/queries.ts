import {gql} from '@urql/core';

/**
 * Fetch a single movie with its cast and creators. Cast/creator data is resolved
 * by movie-service over gRPC to person-service — the first vertical slice.
 */
export const MovieDetailDocument = gql`
	query MovieDetail($id: ID!) {
		movie(id: $id) {
			id
			title
			synopsis
			releaseYear
			genre
			artworkUrl
			createdAt
			updatedAt
			cast {
				id
				characterName
				person {
					id
					name
				}
			}
			creators {
				id
				job
				person {
					id
					name
				}
			}
		}
	}
`;
