"""开发时生成新场景/旧日龄通知共用的 XML 按钮动效；不生成或重绘业务图标。"""
from pathlib import Path


def generate(res: Path):
    animator = res / 'animator'
    animator.mkdir(exist_ok=True)
    (animator / 'notification_button_pulse.xml').write_text('''<!-- 仅变换背景，文字与点击区域保持稳定；完整呼吸周期 1100ms。 -->
<set xmlns:android="http://schemas.android.com/apk/res/android" android:ordering="together">
    <objectAnimator android:propertyName="scaleX" android:valueFrom="1" android:valueTo="0.90" android:valueType="floatType" android:duration="550" android:repeatCount="infinite" android:repeatMode="reverse" android:interpolator="@android:interpolator/accelerate_decelerate" />
    <objectAnimator android:propertyName="scaleY" android:valueFrom="1" android:valueTo="0.86" android:valueType="floatType" android:duration="550" android:repeatCount="infinite" android:repeatMode="reverse" android:interpolator="@android:interpolator/accelerate_decelerate" />
</set>
''')
    for wave, offset in [('a', 0), ('b', 700)]:
        (animator / f'notification_wave_scale_{wave}.xml').write_text(f'''<set xmlns:android="http://schemas.android.com/apk/res/android" android:ordering="together">
    <objectAnimator android:propertyName="scaleX" android:valueFrom="0.20" android:valueTo="3.4" android:valueType="floatType" android:duration="1400" android:startOffset="{offset}" android:repeatCount="infinite" android:repeatMode="restart" android:interpolator="@android:interpolator/linear" />
    <objectAnimator android:propertyName="scaleY" android:valueFrom="0.20" android:valueTo="3.4" android:valueType="floatType" android:duration="1400" android:startOffset="{offset}" android:repeatCount="infinite" android:repeatMode="restart" android:interpolator="@android:interpolator/linear" />
</set>
''')
        (animator / f'notification_wave_fade_{wave}.xml').write_text(f'''<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android" android:propertyName="strokeAlpha" android:valueFrom="0.70" android:valueTo="0" android:valueType="floatType" android:duration="1400" android:startOffset="{offset}" android:repeatCount="infinite" android:repeatMode="restart" android:interpolator="@android:interpolator/linear" />
''')
    for surface, effect, sweep, w, h, color in [
        ('scene_button_surface', 'scene_button_shimmer', 'scene_button_sweep', 295, 39, '#0D60F1'),
        ('scene_button_surface_compact', 'scene_button_shimmer_compact', 'scene_button_sweep_compact', 77, 32, '#0D60F1'),
        ('notification_button_surface', 'notification_button_motion', 'notification_button_sweep', 295, 46, '#0881FE'),
        ('notification_button_surface_compact', 'notification_button_motion_compact', 'notification_button_sweep_compact', 77, 32, '#0881FE'),
    ]:
        r = h / 2
        pill = f'M {r},0 H {w-r} A {r},{r} 0 0 1 {w},{r} A {r},{r} 0 0 1 {w-r},{h} H {r} A {r},{r} 0 0 1 0,{r} A {r},{r} 0 0 1 {r},0 Z'
        glow = w * .18
        circle = f'M {-glow},{r} a {glow},{glow} 0 1 0 {glow*2},0 a {glow},{glow} 0 1 0 {-glow*2},0 Z'
        rx, ry = w * .16, h * .6
        ellipse = f'M {w/2-rx},{r} a {rx},{ry} 0 1 0 {rx*2},0 a {rx},{ry} 0 1 0 {-rx*2},0 Z'
        waves = '\n'.join(f'''        <group android:name="wave_{wave}" android:pivotX="{w/2}" android:pivotY="{r}" android:scaleX="0.20" android:scaleY="0.20">
            <path android:name="wave_line_{wave}" android:pathData="{ellipse}" android:strokeColor="#FFFFFF" android:strokeWidth="1.2" android:strokeAlpha="0" android:fillColor="@android:color/transparent" />
        </group>''' for wave in ['a', 'b'])
        (res / f'drawable/{surface}.xml').write_text(f'''<!-- 背景呼吸、圆角裁切、双层水波纹与单向扫光；不移动文字或 View 边界。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{w}" android:viewportHeight="{h}">
    <group android:name="pulse" android:pivotX="{w/2}" android:pivotY="{r}">
        <path android:fillColor="{color}" android:pathData="{pill}" />
        <clip-path android:pathData="{pill}" />
{waves}
        <group android:name="sweep" android:translateX="{-glow*1.1:.3f}">
            <path android:fillColor="#52FFFFFF" android:pathData="{circle}" />
        </group>
    </group>
</vector>
''')
        (animator / f'{sweep}.xml').write_text(f'''<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android" android:propertyName="translateX" android:valueFrom="{-glow*1.1:.3f}" android:valueTo="{w+glow*1.1:.3f}" android:valueType="floatType" android:duration="1400" android:repeatCount="infinite" android:repeatMode="restart" android:interpolator="@android:interpolator/linear" />
''')
        wave_targets = '\n'.join(f'''    <target android:name="wave_{wave}" android:animation="@animator/notification_wave_scale_{wave}" />
    <target android:name="wave_line_{wave}" android:animation="@animator/notification_wave_fade_{wave}" />''' for wave in ['a', 'b'])
        (res / f'drawable/{effect}.xml').write_text(f'''<animated-vector xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/{surface}">
    <target android:name="pulse" android:animation="@animator/notification_button_pulse" />
{wave_targets}
    <target android:name="sweep" android:animation="@animator/{sweep}" />
</animated-vector>
''')


if __name__ == '__main__':
    generate(Path(__file__).resolve().parents[1] / 'notification/src/main/res')
