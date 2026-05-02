// PATCH key parts only

private lateinit var startStretchButton: Button
private lateinit var startRecoveryButton: Button

private fun bindViews() {
    homeView = findViewById(R.id.homeView)
    classView = findViewById(R.id.classView)
    previewView = findViewById(R.id.previewView)
    overlayView = findViewById(R.id.overlayView)
    coachText = findViewById(R.id.coachText)
    flowName = findViewById(R.id.flowName)
    progressText = findViewById(R.id.progressText)
    countdownText = findViewById(R.id.countdownText)
    llmStatus = findViewById(R.id.llmStatus)
    progressBar = findViewById(R.id.progressBar)
    startClassButton = findViewById(R.id.startClassButton)
    startStretchButton = findViewById(R.id.startStretchButton)
    startRecoveryButton = findViewById(R.id.startRecoveryButton)
    startButton = findViewById(R.id.startButton)
    pauseButton = findViewById(R.id.pauseButton)
    restartButton = findViewById(R.id.restartButton)
}

private fun setupButtons() {
    startClassButton.setOnClickListener {
        loadPlaylist(listOf(
            "flows/01_mountain_warmup.flow.txt",
            "flows/02_forward_fold_main.flow.txt",
            "flows/03_twist_cooldown.flow.txt"
        ))
    }

    startStretchButton.setOnClickListener {
        loadPlaylist(listOf("flows/02_forward_fold_main.flow.txt"))
    }

    startRecoveryButton.setOnClickListener {
        loadPlaylist(listOf("flows/03_twist_cooldown.flow.txt"))
    }
}

private fun loadPlaylist(paths: List<String>) {
    val flows = paths.map { FlowLoader.loadFromAssets(this, it) }
    playlist.setPlaylist(flows)
    currentFlow = playlist.current()!!
    currentPose = YogaPoseCatalog.poses.firstOrNull { it.id == currentFlow.pose }
        ?: YogaPoseCatalog.poses.first()

    showClass()
    sessionState = SessionState.IDLE
    updateUi()
}
