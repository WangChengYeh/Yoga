import bpy
import math
import os

def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete()

def create_bone(armature_obj, edit_bones, name, head, tail, parent_name=None):
    bone = edit_bones.new(name)
    bone.head = head
    bone.tail = tail
    bone.use_connect = False
    if parent_name:
        bone.parent = edit_bones[parent_name]
    return bone

def build_armature():
    armature_data = bpy.data.armatures.new("ArmatureData")
    armature_obj = bpy.data.objects.new("Armature", armature_data)
    bpy.context.collection.objects.link(armature_obj)
    bpy.context.view_layer.objects.active = armature_obj
    armature_obj.select_set(True)
    
    bpy.ops.object.mode_set(mode='EDIT')
    ebs = armature_obj.data.edit_bones
    
    # Coordinates for a roughly 1.7m tall humanoid in T-Pose
    # Format: (x, y, z)
    create_bone(armature_obj, ebs, "Hips", (0, 0, 0.9), (0, 0, 1.0))
    create_bone(armature_obj, ebs, "Spine", (0, 0, 1.0), (0, 0, 1.15), "Hips")
    create_bone(armature_obj, ebs, "Spine1", (0, 0, 1.15), (0, 0, 1.3), "Spine")
    create_bone(armature_obj, ebs, "Spine2", (0, 0, 1.3), (0, 0, 1.45), "Spine1")
    create_bone(armature_obj, ebs, "Neck", (0, 0, 1.45), (0, 0, 1.55), "Spine2")
    create_bone(armature_obj, ebs, "Head", (0, 0, 1.55), (0, 0, 1.75), "Neck")
    
    # Left Arm
    create_bone(armature_obj, ebs, "LeftShoulder", (0, 0, 1.45), (0.15, 0, 1.45), "Spine2")
    create_bone(armature_obj, ebs, "LeftArm", (0.15, 0, 1.45), (0.45, 0, 1.45), "LeftShoulder")
    create_bone(armature_obj, ebs, "LeftForeArm", (0.45, 0, 1.45), (0.75, 0, 1.45), "LeftArm")
    create_bone(armature_obj, ebs, "LeftHand", (0.75, 0, 1.45), (0.85, 0, 1.45), "LeftForeArm")
    
    # Right Arm
    create_bone(armature_obj, ebs, "RightShoulder", (0, 0, 1.45), (-0.15, 0, 1.45), "Spine2")
    create_bone(armature_obj, ebs, "RightArm", (-0.15, 0, 1.45), (-0.45, 0, 1.45), "RightShoulder")
    create_bone(armature_obj, ebs, "RightForeArm", (-0.45, 0, 1.45), (-0.75, 0, 1.45), "RightArm")
    create_bone(armature_obj, ebs, "RightHand", (-0.75, 0, 1.45), (-0.85, 0, 1.45), "RightForeArm")
    
    # Left Leg
    create_bone(armature_obj, ebs, "LeftUpLeg", (0.1, 0, 0.9), (0.1, 0, 0.45), "Hips")
    create_bone(armature_obj, ebs, "LeftLeg", (0.1, 0, 0.45), (0.1, 0, 0.1), "LeftUpLeg")
    create_bone(armature_obj, ebs, "LeftFoot", (0.1, 0, 0.1), (0.1, -0.15, 0), "LeftLeg")
    create_bone(armature_obj, ebs, "LeftToeBase", (0.1, -0.15, 0), (0.1, -0.2, 0), "LeftFoot")
    
    # Right Leg
    create_bone(armature_obj, ebs, "RightUpLeg", (-0.1, 0, 0.9), (-0.1, 0, 0.45), "Hips")
    create_bone(armature_obj, ebs, "RightLeg", (-0.1, 0, 0.45), (-0.1, 0, 0.1), "RightUpLeg")
    create_bone(armature_obj, ebs, "RightFoot", (-0.1, 0, 0.1), (-0.1, -0.15, 0), "RightLeg")
    create_bone(armature_obj, ebs, "RightToeBase", (-0.1, -0.15, 0), (-0.1, -0.2, 0), "RightFoot")
    
    bpy.ops.object.mode_set(mode='OBJECT')
    return armature_obj

def build_placeholder_mesh(armature_obj):
    # Add a simple skin modified mesh to represent the body
    bpy.ops.mesh.primitive_cube_add(size=2, location=(0,0,0))
    mesh_obj = bpy.context.active_object
    mesh_obj.name = "CoachMesh"
    
    # We will just merge all vertices to center, add a skin modifier, and use the armature
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.merge(type='CENTER')
    bpy.ops.object.mode_set(mode='OBJECT')
    
    # Add skin modifier
    skin_mod = mesh_obj.modifiers.new(name="Skin", type='SKIN')
    
    # Create vertices at bone locations
    mesh = mesh_obj.data
    verts = []
    edges = []
    
    # mapping from bone to vertex index
    bone_to_idx = {}
    
    # Get bone head/tail coords
    bpy.context.view_layer.objects.active = armature_obj
    bpy.ops.object.mode_set(mode='EDIT')
    bones = armature_obj.data.edit_bones
    
    # We will just collect heads and tails
    points = []
    point_to_bone = []
    
    for b in bones:
        points.append(b.head)
        points.append(b.tail)
        
    bpy.ops.object.mode_set(mode='OBJECT')
    bpy.context.view_layer.objects.active = mesh_obj
    
    # Actually, the easiest placeholder is automatic weights on a simple blocky model.
    # We will generate a cylinder/cube for each bone and join them.
    bpy.ops.object.select_all(action='DESELECT')
    mesh_obj.select_set(True)
    bpy.ops.object.delete()
    
    parts = []
    for bone in armature_obj.data.bones:
        head = bone.head_local
        tail = bone.tail_local
        length = (tail - head).length
        center = (head + tail) / 2
        
        # Add a cube
        bpy.ops.mesh.primitive_cube_add(size=1)
        part = bpy.context.active_object
        
        # Scale and move
        part.scale = (0.05, 0.05, length / 2)
        part.location = center
        
        # Align rotation to bone direction
        vec = tail - head
        rot = vec.to_track_quat('Z', 'Y')
        part.rotation_euler = rot.to_euler()
        
        parts.append(part)
        
    # Join all parts
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    final_mesh = bpy.context.active_object
    final_mesh.name = "CoachBody"
    
    # Parent to armature with automatic weights
    bpy.ops.object.select_all(action='DESELECT')
    final_mesh.select_set(True)
    armature_obj.select_set(True)
    bpy.context.view_layer.objects.active = armature_obj
    
    bpy.ops.object.parent_set(type='ARMATURE_AUTO')
    
    return final_mesh

def main():
    clear_scene()
    armature = build_armature()
    build_placeholder_mesh(armature)
    
    # Ensure export directory exists
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, ".."))
    export_dir = os.path.join(project_root, "godot", "assets", "avatars")
    os.makedirs(export_dir, exist_ok=True)
    
    export_path = os.path.join(export_dir, "female_yoga_coach.glb")
    
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.export_scene.gltf(
        filepath=export_path,
        export_format='GLB',
        use_selection=True,
        export_yup=True,
        export_texcoords=True,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False
    )
    
    print(f"Placeholder rig exported successfully to {export_path}")

if __name__ == "__main__":
    main()
