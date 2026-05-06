# Avatar Notes

The avatar source is the Ch47 character model from the CharacterCreator/ActorCore pipeline.

The skeleton is a standard Mixamo rig using the `mixamorig1` bone prefix.

Textures include Diffuse, Normal, Specular, Glossiness, and Emissive maps. The material uses the `KHR_materials_specular` extension.

The GLB has no embedded animations. The `idle` and `forward_fold` animations are added programmatically in Godot by `AvatarController.gd`.

Godot import uses `animation/import=true` and `skins/use_named_skins=true`, imported as a `PackedScene`.

The license is unknown and must be verified before shipping.

Current limitations:

- No IK.
- No per-bone correction highlight.
- No Mixamo retarget yet.

To add more animations, either embed them in the GLB with Mixamo retargeting in Blender or extend `_setup_animations()` in `AvatarController.gd`.
