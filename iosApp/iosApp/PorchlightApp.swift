import Shared
import SwiftUI

/// The iOS host: one full-screen view showing the shared game.
@main
struct PorchlightApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Whether a round is live. While it is, the status bar and home indicator hide, and swipes from
/// the screen's edges need a second swipe, so a card near an edge never sends the game away.
final class GameState: ObservableObject {
    @Published var immersive = false
}

struct ContentView: View {
    @StateObject private var state = GameState()

    var body: some View {
        GameView(state: state)
            // The game draws under the notch and home bar itself and keeps its controls in the safe area.
            .ignoresSafeArea()
            .background(Color("LaunchBackground"))
            .preferredColorScheme(.dark)
            .statusBarHidden(state.immersive)
            .persistentSystemOverlays(state.immersive ? .hidden : .automatic)
            .defersSystemGestures(on: state.immersive ? .all : [])
    }
}

/// Hosts the Compose view controller from Shared.framework.
struct GameView: UIViewControllerRepresentable {
    let state: GameState

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(arguments: ProcessInfo.processInfo.arguments) { on in
            DispatchQueue.main.async { state.immersive = on.boolValue }
        }
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
