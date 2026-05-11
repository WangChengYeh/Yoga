package com.yogaflow.coach

class DetectionMapperSession(
    private val forwardFoldDetectionMapper: ForwardFoldDetectionMapper = ForwardFoldDetectionMapper(),
    private val twistDetectionMapper: TwistDetectionMapper = TwistDetectionMapper(),
    private val squatDetectionMapper: SquatDetectionMapper = SquatDetectionMapper(),
    private val bridgeDetectionMapper: BridgeDetectionMapper = BridgeDetectionMapper(),
    private val mountainDetectionMapper: MountainDetectionMapper = MountainDetectionMapper()
) {
    val poseDetectionRouter: PoseDetectionRouter = PoseDetectionRouter(
        forwardFoldDetectionMapper = forwardFoldDetectionMapper,
        twistDetectionMapper = twistDetectionMapper,
        squatDetectionMapper = squatDetectionMapper,
        bridgeDetectionMapper = bridgeDetectionMapper,
        mountainDetectionMapper = mountainDetectionMapper
    )

    fun resetAll() {
        forwardFoldDetectionMapper.reset()
        twistDetectionMapper.reset()
        squatDetectionMapper.reset()
        bridgeDetectionMapper.reset()
        mountainDetectionMapper.reset()
    }
}
