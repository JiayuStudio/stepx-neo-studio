import AppKit
let image = NSImage(contentsOfFile: CommandLine.arguments[1])!
for path in CommandLine.arguments.dropFirst(2) {
 print("setIcon", NSWorkspace.shared.setIcon(image, forFile:path, options:[]), path)
 let icon=NSWorkspace.shared.icon(forFile:path)
 icon.size=NSSize(width:256,height:256)
 let rep=NSBitmapImageRep(data:icon.tiffRepresentation!)!
 try! rep.representation(using:.png,properties:[:])!.write(to:URL(fileURLWithPath:path+"/folder-icon-verified.png"))
}
