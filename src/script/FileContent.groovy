package script

import common.Conf

Map params = super.binding.getProperty('params')

String path = params.path
String pathLocal = Conf.isWindows() ? path.replace('/c/Users/', 'C:/Users/') : path
def f = new File(pathLocal)
if (!f.exists()) {
    return [content: '']
}

if (f.isFile()) {
    return [content: f.canRead() ? f.text : '']
} else {
    if (!f.canRead()) {
        return [content: '']
    } else {
        def list = f.listFiles()
        def sortedList = list.sort { a, b ->
            a.name <=> b.name
        }
        return [content: sortedList.collect {
            it.name.padRight(50, ' ') + ' - ' + (it.isDirectory() ? 'dir' : 'file')
        }.join("\r\n")]
    }
}