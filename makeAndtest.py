import shutil, os, sys, platform, subprocess, logging, time
from subprocess import Popen, PIPE, STDOUT

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# create a file handler
handler = logging.FileHandler('build.log', mode="w+")
handler.setLevel(logging.INFO)

# create a logging format
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)

# add the handlers to the logger
logger.addHandler(handler)


def rmtreesafe(path):
    try:
        shutil.rmtree(path)
    except FileNotFoundError:
        logger.info(path + "is not Found")


def rmfilesafe(file):
    try:
        os.remove(file)
    except FileNotFoundError:
        logger.info(file + "is not Found")


def exceBat(cmd):
    logger.info("exceBat:" + cmd)
    p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
    curline = p.stdout.readline()
    while (curline != b''):
        logger.info(curline)
        curline = p.stdout.readline()

    p.wait()
    logger.info(p.returncode)


def mkdir(path):
    logger.info("mkdir:" + path)
    folder = os.path.exists(path)
    if not folder:
        os.makedirs(path)
        logger.info("new folder:" + path)
    else:
        logger.info("There is this folder")
        rmtreesafe(path)
        os.makedirs(path)


def checkAdb():
    p = subprocess.Popen("adb devices", shell=True, stdout=subprocess.PIPE)
    curline = p.stdout.readline()
    while (curline != b''):
        logger.info(curline)
        if "List" in str(curline, encoding="utf-8"):
            curline = p.stdout.readline()
            if curline != b'\r\n':
                return 1
            else:
                return 0
        else:
            curline = p.stdout.readline()
    p.wait()
    logger.info(p.returncode)


def cleanOldFile():
    logger.info("cleanOldFile")
    rmtreesafe("app/build/outputs")
    rmtreesafe("app_discover/build/outputs")
    rmtreesafe("app_discover/build/reports")
    rmtreesafe("app_gms/build/outputs")
    rmtreesafe("app_go/build/outputs")
    rmtreesafe("testreport")


def makeapk(iswin):
    if iswin:
        exceBat("cmd.exe /c" + "gradlew.bat assembleDebug")
    else:
        exceBat("./gradlew assembleDebug")


def makereleaseapk(iswin):
    if iswin:
        exceBat("cmd.exe /c" + "gradlew.bat assembleRelease")
    else:
        exceBat("./gradlew assembleRelease")


def doTest(iswin):
    if iswin:
        exceBat("cmd.exe /c" + "gradlew.bat app_discover:test")
        exceBat("cmd.exe /c" + "gradlew.bat app_discover:connectedAndroidTest")
    else:
        exceBat("./gradlew app_discover:test")
        exceBat("./gradlew app_discover:connectedAndroidTest")
    logger.info("copy test result")
    try:
        rmtreesafe("testreport")
        shutil.copytree("app_discover/build/reports", "testreport")
    except FileNotFoundError:
        logger.info("no test dir")


def doTestCoverage(iswin):
    rmtreesafe("app_discover/build/outputs/code_coverage")
    if iswin:
        exceBat("cmd.exe /c" + "gradlew.bat app_discover:createDebugCoverageReport")
    else:
        exceBat("./gradlew app_discover:createDebugCoverageReport")
    coveragefilepath = "app_discover/build/outputs/code_coverage/debugAndroidTest/connected"
    dirs = os.listdir(coveragefilepath)
    for dir in dirs:
        logger.info("rename coverage file:" + dir)
        os.rename(coveragefilepath + "/" + dir, coveragefilepath + "/coverage.ec")
    if iswin:
        exceBat("cmd.exe /c" + "gradlew.bat jacocoTestReport")
    else:
        exceBat("./gradlew jacocoTestReport")


def copyAPKandSO():
    try:
        shutil.copy2("app_discover/build/outputs/apk/release/NewGallery2.apk",
                     "prebuilt/apk/app_discover/")
        shutil.copy2("app_gms/build/outputs/apk/release/USCPhotoEdit.apk", "prebuilt/apk/app_gms/")
        shutil.copy2("app_go/build/outputs/apk/release/NewGallery2.apk", "prebuilt/apk/app_go/")
        # "x86","x86_64"
        archlist = ["arm64-v8a", "armeabi-v7a"]
        for arch in archlist:
            shutil.copy2("module_dehaze/libs/{}/libDehaze.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_imageblendings/libs/{}/libfbextraction.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_imageblendings/libs/{}/libimageblend.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_facedetector/libs/align/{}/libjni_sprd_fa.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_facedetector/libs/detect/{}/libjni_sprd_fd.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_facedetector/libs/verify/{}/libjni_sprd_fv.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_jpegstream/libs/{}/libsprdjni_jpeg.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_bokeh/libs/{}/libsprdsr.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("app/src/main/libs/{}/libtensorflowlite_jni.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("module_smarterase/libs/{}/libInpaintLite.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libjni_sprd_blur.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libjni_sprd_dehaze.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libjni_sprd_facedetector.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libjni_sprd_imageblendings.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libjni_sprd_real_bokeh.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libsprdjni_eglfence2.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libsprdjni_filtershow_filters2.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libsprdjni_jpegstream2.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
            shutil.copy2("out/lib/{}/libjni_sprd_smarterase.so".format(arch),
                         "prebuilt/lib/{}/".format(arch))
    except FileNotFoundError as e:
        logger.exception(e)
    logger.info("finish  copy apk and so.")


def pushTestResource():
    if checkAdb() == 0:
        print("adb is not ready!")
        logger.info("adb is not ready!")
        return 0
    exceBat("adb root")
    exceBat("adb remount")
    exceBat("adb push test_res/. /sdcard")
    exceBat("adb reboot")
    n = 10
    while n > 0:
        time.sleep(10)
        if checkAdb() == 1:
            logger.info("adb is ready!")
            return 1
        n = n - 1
    return 0


sysstr = platform.system()
isWin = False
if (sysstr == "Windows"):
    logger.info("Call Windows tasks")
    isWin = True;
if len(sys.argv) > 1:
    mode = sys.argv[1]
    logger.info("cmd mode:" + mode)
    if mode == "debug":
        makeapk(isWin)
    elif mode == "test":
        if pushTestResource() == 1:
            doTest(isWin)
    elif mode == "release":
        cleanOldFile()
        makereleaseapk(isWin)
        copyAPKandSO()
    elif mode == "coverage":
        if pushTestResource() == 1:
            doTestCoverage(isWin)
    else:
        logger.info("unknow mode")
else:
    # do all work
    makeapk(isWin)
    doTest(isWin)
