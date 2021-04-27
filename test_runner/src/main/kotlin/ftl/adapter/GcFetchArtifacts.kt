package ftl.adapter

import ftl.adapter.google.fetchArtifacts
import ftl.api.Artifacts

object GcFetchArtifacts : Artifacts.Fetch {
    override suspend fun invoke(input: Artifacts.Identity): Pair<String, List<String>> = fetchArtifacts(input)
}
