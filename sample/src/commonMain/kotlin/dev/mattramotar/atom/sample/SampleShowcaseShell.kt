package dev.mattramotar.atom.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mattramotar.atom.generated.rememberPostAtom

@Composable
fun SampleShowcaseShell(modifier: Modifier = Modifier) {
    val atom = rememberPostAtom(params = PostParams(id = "phase-1"))
    val state by atom.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Atom Showcase Board (Phase 1)",
            style = MaterialTheme.typography.headlineSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShellZone(
                title = "Task List",
                body = "Scaffolded list zone. Seed atom id: ${state.id}.",
                modifier = Modifier.weight(1f)
            )
            ShellZone(
                title = "Task Detail",
                body = "Scaffolded detail zone. Phase 2 atom orchestration plugs in here.",
                modifier = Modifier.weight(1f)
            )
            ShellZone(
                title = "Diagnostics",
                body = "Scaffolded diagnostics zone. Runtime transitions and effects land here.",
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(onClick = { atom.intent(PostIntent) }) {
            Text("Dispatch Seed Intent")
        }
    }
}

@Composable
private fun ShellZone(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
