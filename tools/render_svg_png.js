// 开发时把 Figma 导出的原版 SVG 按整数倍栅格化为透明 PNG（sharp/librsvg），不进入应用构建。
// 用法：node tools/render_svg_png.js <sharp 模块路径> <输入.svg> <输出.png> <倍率>
const [, , sharpPath, source, target, scale] = process.argv;
require(sharpPath)(source, { density: 72 * Number(scale) })
  .png()
  .toFile(target)
  .then(info => console.log(target, info.width, info.height));
