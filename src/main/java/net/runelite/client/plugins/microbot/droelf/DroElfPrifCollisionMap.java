package net.runelite.client.plugins.microbot.droelf;

import net.runelite.api.coords.WorldPoint;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Self-contained Prifddinas collision graph, taken from the Dro Efficient Walker
 * v1.11.2 collision-map data.
 *
 * The city occupies four 64x64 map chunks:
 *   50_94, 50_95, 51_94, 51_95
 *
 * Each tile stores two passable-edge bits:
 *   edge 0 = north edge from (x,y) to (x,y+1)
 *   edge 1 = east  edge from (x,y) to (x+1,y)
 *
 * South/west movement reads the corresponding north/east edge on the adjacent tile.
 * The bit layout is:
 *   ((((plane * 64) + localY) * 64 + localX) * 2) + edge
 *
 * This is intentionally Prif-only. It does not depend on Efficient Walker at runtime.
 */
final class DroElfPrifCollisionMap {
    static final int MIN_X = 3200;
    static final int MAX_X = 3327;
    static final int MIN_Y = 6016;
    static final int MAX_Y = 6143;

    private static final Map<Integer, byte[]> CHUNKS = new HashMap<>();

    static {
        CHUNKS.put(key(50, 94), Base64.getDecoder().decode(
                "wAcAvwIA/67+/78HKACsx4ACAArwASsA6P8fAgAABP8AAAAA8L8AAMD/fwAAwAL/HwAAAPAfAADAqz8AAWig+g8AAMD/DwAAoAAK"
                + "AACAAPAHAADq/0cAAAAAAAAAAACgBwABwL8CAAAAAAAAAAAAAAcAAcAPAAAAAAAAAAAAAACqAAHABwAAAAAAQEDABwDwAMD+/wYA"
                + "AAAAAHBwwAcH/AAg7H8AAEDAAQD/f8D/B/wAAMR/AADA/v//////////AQP/rwAAQPz//6/+/////wHx/wcAAP7///8GrLru7/8B"
                + "/78DAAD8v+ovBAQQwIP///8fAAAA/B/ABwIIIMAB////PwAAAPwvgALAAQDAAPv//w8AAPC/BwBwgMACQADw//8GAAD8HwYaIAAA"
                + "AGwA8P8vAAAA+B8sEAAfAAAAAPD/AgAAwPEfAPAHCwAAEADwPwAAAPD/CwCwqgBwAKAK/goAAADwvwAAEAAArAEEQOgAAADg/xsA"
                + "ABocAQcHxEfAAAAAxL8AAAEQDAEHB4RH8AAAAPwPAAAAAAQB+gIEg/AAAAD8AgAAAAD/AHAA+AP4AQCgfwAAAAAAf3xw8PEB8AMA"
                + "AH8AAAAAAP7/cfz/AfABAAB/AAAAAAD4////vwHwAgAAfwAA+AEA8P+r/h8B8AAA8H8AAKD/f/D/rvu/AfBAAOB/AAwA/3/wfwDw"
                + "H6D7MADA/x8AAL/q/9/93X8E8RAAwP9/AAAfgO7f/d3///8gAMC//v//CwDA3/3d//77AADAC/z//wAAwN+o2C+s4QAE/wH8v3oA"
                + "AMB//PEHAMBABP4Aqh+wqgH8/6v+AwDAQAB8QAAfEBrB/////wEA/EAAeEAcHxAQwf+//6/rq/pAAHBAvn8QH8H/C/rHwQHwwAHg"
                + "QSh/EAzB/0Hw9sPx8fAAwEEA6v+/wb8AcPSR8fFwAMCBMMD/H/8fAPCnEMHgcADAAQig////GwDwB8Dww3AAwA8AAL6/qhEA4IdC"
                + "8MFgAMEHAB8YHwAKAMC/f/DBwAHAB8Aa8A8AHAHAH////4AB+Adw8P8DcD4BwP////8AAfAHYLCqAf8fAfy/+uv+AAbwB4AfAAF/"
                + "Oh74C6BA/EAA+gcACgBxPwCs8EEAgPpAAPB/AADBoR+/BvDAAhzgQADwL/Dx/wH/H8RxKAAIwEAA8Aeg/ur//x/EcAAAAMCAAegH"
                + "QDjE/v///38AAQDAAAPAH7T/W6j/////AQEA/MAAwA8U/tEB//rr/wEBAP9AAMCDZ7iO8D/wgf8AAMD/AAD+AQd8ACAPqgJ/EADA"
                + "/wAA/AGq/g8AAwAAe3AAwP//B/8BAPwHggEAAHFwAP+q/wf/PwD8BxCAQTB/MMB/AP//////////////////////////////////"
                + "/////////////////////////////////////6/q///////////////////Dh///////////////////8B/+////////////////"
                + "f/x/qKqqqqqqqvr//////3////////////////////9/////////////////////P////////////////////w//////////////"
                + "///////D////////////////////8P//////////////////P/z//////////////////w/////////////////////D////////"
                + "////////////8P//////////////////P/z//////////////////w/////////////////////D/////////6r6////////8P//"
                + "//+vqj4A4Kuq////P/z/////BwAccMABAP///w///////wcAHPzBAQD//6/C//////8HABz/xwEA///D////////BwAc/8cBAP//"
                + "8P///////wcAHP7DAQD/f/z///////8HAByowAEA/3////////////9/APD///9/////////////////////f/7/////////////"
                + "///r/3/4////////////////wf//4f///////////6r6/8H//8f//////////38A8P/////f//////////9/APD/////3///////"
                + "////fwDw/////9///////////38A8P/////f//////////9/APD/////3/+/qv6vqv77fwDw/////9//HwD8BwD88f/////////f"
                + "/x8A/AcAvP///6uqqur/3/8fAPwHABz///8BAADA/9//HwD8BwD8//v/AQAAwP/f/x8A/AcA/P/x/wEAAMD/3/8fAPwHAPy//+8B"
                + "AADA/9//HwD8////H//HAQAAwP/f//+vqv///////wEAAMD/3///AwD+/7+q/v8BAP///9///wEf/P8fAPz/AQD////f///Bf/z/"
                + "HwD4//7/////3///8X/8qh4A8H/8/////9///+F/fAAcAPzv///////f//+BP3wAHACowv//////3///AQp8ABwAAMD//////9//"
                + "/wcAfwAcAADA////6//f//+rqioA/P8BwP/vq8D/3///AQAAAPz/AcD/xwGA/9///wEAAKr+/wHA//4BAP/f//8BAAAA/P///3/8"
                + "Af//3///AQAAAPz//////8H//9///wEAAAD8/////6rA///f//8BAAAA/P///38AwP//3///AQAAAPz///9/AMD/////////fwD8"
                + "////f8D//////////////////z/w//v/////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "///////////////////////////////////////////////////////////////////////////////////////7/v//////////"
                + "//////+/IOj/////////////////D/+H/////////////////8OqGv7///////////////8xAGD8////////////////HADA+f//"
                + "////////////fxyswfH///////////////8dB8f9////////////////HAfH+f//////////////fxz+w/H///////////////8c"
                + "+MD8//////////////8/fHAw/P//////////////D///D////////////////8O/qsL////////////////wD3H8////////////"
                + "/6s+/MP///////////////9xCP/w/////////////////R8//P////////////////n/C//////////////////x/8H/////////"
                + "////////9//x/////////////////+P/+f/////////////////A/+H///////////////8//KqH////////////////D38QHv7/"
                + "/////////////8M/fHj4////////////v+/wD//hofv//////////wsC/MP/hx/g///////////w///w/x//h/////////8/rKo/"
                + "/P9/vh7/////////HwMAHv//fxyc/////////88BH5z///8cHP/////////HwXoc//9/+H/+////////30Hw3////6F++P//////"
                + "/89B8J7///9H6OH////////HgT8c/////4eH////////nwEKzP////8fHv7//////x8GAMP/////f3j4r+7///9/+P/w///////h"
                + "4UOA/////6Eq/P//////h4fwqv////8fx////////x/+fwD///////////////9//H8A//////////////////0/BP//////////"
                + "///////8H/j///////////////8//B/g////////////////H/96wP///////////////58r4P////////////////8fAcCr////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "//////////////+/qur/////////////////HwDA/////////////////x8AwP////////////////8fAMD/////////////////"
                + "HwDA/////////////////x8AwP////////////////8fAMD/////////////////HwDA/////////////////x8AwP//////////"
                + "////////Afz/////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////r/r//////////6uq/v///wfw//////////8BAPz///8H8P//////"
                + "////AQD4////B/D//////////wEA8P///wfw//////////8BAPD/////////////////AQDw//////////+q/////wEA8P//////"
                + "//9/AP////8BAPz/////////fwD/////AQD8/////////38A//////////////////9/AP//////////////////fwD/////////"
                + "/////////38A//////////////////9/AP//////////////////KgD/////////////////fwAA/////////////////38AAP//"
                + "//////////////9/AAD///////////////+qKgAA////////q6r///9/"));
        CHUNKS.put(key(50, 95), Base64.getDecoder().decode(
                "/wf/HwT8fxAcQAD/H/B/AKoC/x/8v/4f/Ef8/x/wfwAAAPr//x/8/////////38AQADw//8f/P//////v/p/AEAA4L+v//++rur/"
                + "/wrwfwBgAMALAvovCADAv3oA4H8AwALAAavmB6wawAtwAIB/AEAAwAEBxQcEEPABcAAA6v/AAPAB7f//xxHwAXAgAMD/QADwAcXu"
                + "q8YR/B98AADA/0AA/wEBxAHE0b8e/AcAgP9wAP8BqrIA6csf/P+6AAD+MADrKwQfAMKxHrx+EAAA6BAAwQEEGgDwH/wffAAAAMBw"
                + "AMABAnAA4BvoC3wAAADAfADwASAgEMQRwAH4AQCswHgA8AEAAB7MGcAB8B8EAOCwAuoHAR+4w+H+B/wfBADAEADAB8EaEMAB/P//"
                + "fwQA8DAAwB9x8P////+v6v////8QAMC/YLC66qu+AsDr////MAT/H4AfEMFBHADAgaqq/xAC/w8ACqjGsQoAwAEAAP9wAP8HBwAB"
                + "zBmAAPABgA7rcAD7A6vqAcQBAADgAQAAwXwA8AEBwP//wRoAwf/fH8F8APABAcD//wCg/8H//////APwAcL/qn4AAP/6+v////8A"
                + "/wfAfwA8AAB/cPDv//8/AP8fwH8ADAAA6vr6hyrqDwD/f7A+AAMAAEBw8AcEwAYA+j8QHN4BAADA//8HCsAAAPAf0L7dAQAAwHP+"
                + "HwDAAADwH0h/+QEAAPBw+GoAwAAA8B9AevEBAAAw/GGAB8AAALB/QHDxAQAAEK9HAALABAAQ/18q8QEAEMAHR0ABwACArv+fqvAB"
                + "AAzw+vov//EAAAT//wHgAQAEMHDgB///AAAA//8BwAEABAxwgAf/+wAAAOv/AcAHAMQHcAB/q/AAAACA/gGAf/D/A3AAPwD6ABoA"
                + "APwBAP+rvgBwAB8f8ABgAAD4AcC/EBwccMC/GvAAgAEA8B+AehAcHK/H/xHwAAA+APB/ACCQqhwHx/f3+gAAGADw/x8ACgAcBsOn"
                + "cvAAAOADoP8fAAAIGPiABjDwAACAAAD+/38AAAAgAOgf8AAAABwArP//HwBAABCAGvAAAAAYAADr/x8AwP8fAQDwAAAA4AIAwf7/"
                + "f8D//wfA/wAAAAAAAAH4r/7/r/4f8P8AAAAADgAH8Afs6weo+v//AAAAAAAAAvwHxMEHAPD//38QAAAAAQCoAqiAAgCgqqr/fwAA"
                + "wAEQAAAAAAAAAAAAquoBAIAHEAAAAAAAAAAAAADABwAADwAAAAAAABAsAPAAgB8AAAYAAMARAABxBAD8AAB6AAAAAACgOgCA/x8I"
                + "/wAAcAQAAIYBAAAAAO8fAP8AAKAFAAAADwAAeACCHsH/AAAAHwAAoIIAASAAAKz/6v///////////////x/w//H/////////////"
                + "//8f8P///9////////////////H////f/////////////7/w////3/+/qur/qqr///8f8P/7/9//HwDAfwAA////H/D/8f/f/x8A"
                + "gD8AAP///3/g////3/8fAAAfAAD///9/gOr//9//HwAQHwAA////fwDA///f/x8AAB8AAP///38AwP//3/8fAAAfAAC//v//r8H/"
                + "/9//HwDAfwAAH/z//waB///f/x8AwH8AAB/8/38EAar/3///////B/D/////7wEA/9///////////////8cBwP/f//+vqv/v+///"
                + "/////8H/3///AwD+x/H//////////9///wEf/MKh///v///////f///Bf3zAAf//x///////3///8X/8////+//+/////9///+F/"
                + "/Oqr//F//K+q/v/f//+BP3zAAf+//v8HAPz/3///AQr8x/H/H/z/BwD8/9///wcA/8fx/x/8/wcA/P/f/////////7//7/8HAPz/"
                + "3/////////8f/8f/BwD8/9////////////v//wcA/P/f///////////x//8HAPz/3////////7//////BwD8/9////+q//8f////"
                + "/wcA/P/P//8vAPr///v///8HAPz/w///BwDw///x////BwD8//D//wcA8P/6/////wcA/H/8//8HAPB/8P////8HAPx/////BwDw"
                + "f/D/////////f////wcA8P///////////3/+//8HAPD///////////9/+P//BwDw/////////////+H//wcA8P////////////+H"
                + "6v///////////////////4f///////////////////8f/v////+vqvqq+v//////f/j/////BwAwAOCr6v/////h/////wcAEPzB"
                + "AcD/////h/////8HABD/xwHA/////x/+////BwAQ/8cBwP////9/+P///wcAEP7DAcD//////+H///8HABD4wAHA//////+H////"
                + "BwAQIMABwP//////H/7/////fwDw/////////3/4////////////////////4f///////////////////4f/////////////////"
                + "//8f////////////////////f////////////////////3////////////////////9//v+qqqqqqqqq+v//////f/g//P//////"
                + "///////////hD//////////////v////h8L/////////////x///////////////////////////////////////////////////"
                + "/////////6qqqqqqqqqqqqqqqqqqqqr///////////////8/AcCh////////////////H6HHBf///////////////38B/wX/////"
                + "//////////8/Ae8F/////////6qq////H6HCAf///////38AAP///z8BwKH///////9/AAD///8fAcD/////////fwAA////n3/w"
                + "6v///////38AAP///x/+P8D///////9/AAD///9//B/w////////fwAA//////0frP///////38AAP/////8fwD/////v+9/AAD/"
                + "//8//H+g/////wuC/v//////D696AP/////wf/j//////8PD4ar///8/rKrh///////w8AfE////HwMAxv////8/PPz//////88B"
                + "H5z/////Dw/////////HwXoc////78LD////////30Hw3////wP88P///////89B8J7////wf/z/6v/////HgT8c//9/vD7/f8D/"
                + "////nwEKnP///xwc/3/E/////x8GAB/+/38cnP9/xP////9/+P9/+P8//w//f8T//////6Gq/uH/D7/C/3/E//////8fR/iH/8ML"
                + "8f9/xP/////////hH/7w8P//L8T/////////h384PPz//wfE/////////x9+AA////+Hxv//////r7566P/D////B8T//////wcc"
                + "8MH/8f///3/E///////H//H3//n///9/wP//////x//x8//h////f8D//////8f/8fH/gf/////////////H//H9vx/+////////"
                + "////x//x+Qp/+P///////////4eq8CEc/uH///////////8HAPD/f/iH+/7////////////////hHyDo////////////////h///"
                + "h////////////////x/++hr+//////////////9/PHBg/P///////////////xz8wfn//////////////38cr8fx////////////"
                + "////HQfH/f///////////////xwGw/n//////////////38cqMDx////////////////GQDA/P///////////////2EAMPz/////"
                + "//////////+H/w//////////////////H6rC//////////////////9x/P//////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "//////////////////////////////////////////////////////////////////////////+qqqqqqqqqqqqqqqqqqqqq////"
                + "////AQD///9/AAAAAP///////wEA/v//fwAAAAD///////8BAPj//38AAAAA////////AwDw//9/AAAAAP///////wEA8P//fwAA"
                + "AAD///////8BAOD//38AAAAA////////AQCA//9/AAAAAP///////wEAAP////9/AAD///////8BAAD/////fwAA////////AQAA"
                + "/v///38AAP///////wcAAPj///9/AAD///////8HAADg/////38A////////HwAAgP////9/AP///////38AAAD+////fwD/////"
                + "////AQAA/P///38A/////6uq/h8AAPj///9/AP////8BAPz/AQCg+v//fwD/////AQD4/wcAAKD//38A/////wEA8P8fAAAA/v9/"
                + "AP////8BAPD/fwAAAPj/////////AQDw/38AAADw/////////wEA8P//AQAA8P////////8BAPz//wcAAOD/////////AQD8//8f"
                + "AACA////////////////HwAAAP7//////////////x8AAAD8//////////////8fAAAA+P//////////////fwAAAPD/////////"
                + "//////8BAADw////////////////HwAAoPr//////////////38AAACg////////////////BwAAAP7//////////////x8AAAD4"
                + "//////////////9/AAAAoP///////////////wEAAAD///////////////8HAAAA////////////////HwAAAP//////////////"
                + "/38AAAD/////////////////HwAA/////////////////38AAP//////////////q/7/AQD/////////////vwDo/x8A////////"
                + "/////x8AwP//Af////////////8fAMD/////////////////HwDA/////////////////x8AwP////////////////8fAMD/////"
                + "////////////HwDA/////////////////x8AwP////////////////8fAMD/////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////////////////////////////////////////////////////////////////"
                + "////////////////////////////////////////qqqqqqqqqqqqqqqqqqqqqg=="));
        CHUNKS.put(key(51, 94), Base64.getDecoder().decode(
                "H///v/qrB/z/8PErrCrAAP///x+gAAf8Lvj/AQIAQMD///sfAAAGLACg/gEAAEDA7/qwDgAAxAcAAPgBAAAAwAcwEAABAIAaAADw"
                + "EQAIAPwKAAAAIAAAAAAAoB8AAAD8AAAAAAAAAAAAAAAeAADx+wAAAAAAAAAAAABA/AcAv/AHcBDAAQAcAAEEgP4P/B+gH2AQxAEf"
                + "HAABAAD8B/8KAB/A//8BHx8AAUEA+P8vAAD/////////7wFAAKD+BwAA///v/////8d/wAAA/AcAAL+qx+qr////f0AAAPgHGAAP"
                + "AGqAAOr//39AAADwBwDwBwBAAADA//v/PwAAqH8A8B8QQAAAgP/x/x8AAAB/APAffMAaHATq6+p/AAAAbsD/D6hqEBwEwEHAe0AA"
                + "AEDA/wcBQKCqD8BBwPB/AAAgwKsHx8cBAAbAL0DwfwAAAMAB/4fHAQcEwAdw8P8eAADoAf8Hw8EaAMAH+Op/HAAAgAD/x8dxcAD/"
                + "BzCA//8BAAD4////cXDAvu8bAP//AQEA8L/6/6EvAAiHEAD7/78DAPAfoPoBBwQB9xAA8f8fAQDwDwDwBwcC8X8cwOD//wEA+AMA"
                + "4B9H8PG/HXDA//8fAOABAID+f/ApBwwgwKr/LwDAAQAA6P+/H8cHAEAA/gcAwAEAAICqH//v6wcAAPwDAPAHAAAAAOv/x8EqAAD8"
                + "EQD8AwCEAADB/4NAAAcG/AsA/wEABAAAwf8AfBEHBPgBAP8B8AfAAap+AHwRHwTwAcD/AfAHwH8A/AH8Pq9C8AGA/x/gH7/6cfwB"
                + "fAoHQPB/AP8PgP8f8LH9Hz8A38f/PwD/BwD/H/AI/f8f+v/K/gsA/38A/g9w/P+ravAPAPwBAH8vEPwH6P//AUTwBwD/BwB/Bwio"
                + "B8H//3dE/Ouv/wfAfwdAAAeA6v9/xP/BAv8awH8HAAAHDID6f8T/QQB/EMB/rwYEDwAA8CPH/yrwfwCAfwcEAAcAAPABwj8A4H4A"
                + "AP8fBAD/AQDw//8PfED8ARD////B/wGA6v//B6zB/wEQ//////8BAsD/q/8Hx/8BAP//6///BwDA6gGvBsP/HwD/v4Dq/wdAQMAB"
                + "B/zA+n+A/x8AgOsHQHDAABooQOB/AP8LHADBvyogAABwADDAfxD/AQgAgB8APAcA8P4K8D8A/wEACAA/ABgDCnwoAPAfwP8BAAAA"
                + "HwDw////EQDwD+B/HwBAwB8A+P///x8A/AeAf38AQIB+APD///8fAPwHAH//AQAAfAD4////Cwf4HwB//wEAAD8AsKuq/gAC8X/A"
                + "f/8BAAAfABiqAHAoAPp/wH+qfwCgHwAgAAD/AQDwf/D/AP8BAB8AQAAA/0EA8H/w/////////////////////3//////////////"
                + "//////9/////////////////////f///////////q/r//////3////////////Dh//////9//////////z/8h///////f6+qqqqq"
                + "qqoK/x///////3////////////9///////9/////////////f///////f////////////3/+/////3////////////9/+P////9/"
                + "/////////////+H/////f/////////////+H/////3//////////////H/7///9//////////////3/4////f///////////////"
                + "4f///3///////////////4f///9//6uq//////////8f/v//f/8BAP//////////f/j//3//AQCvqv/////////h//9//wEAAwD+"
                + "////////h///f/8BAAEH/P///////x/+/3//AQDBH/z///////9/+P9//wEA8X/8qqr6/////6H6f/////F/fAAA8P//////4X//"
                + "///hP3wAAPD//////4d/////gQp8AADw//////8ff////wcAfwAA8P//////f3///////38AAPD//////39///////9/AADw////"
                + "//8/f///////fwAA8P//////D3///////38AALCq/v///8N////////r//8fAPz////xf///////wf//HwD8/////X///////4D6"
                + "/x8A/P////1//////38A8P8fAKyq///9f/////9/APD/HwAEAP///X//////fwDg/x8ABAD///1//////38AwP8fAAQA///9f///"
                + "//9/APCqCgAEAP///X//////fwBwAAD/BwD///1//////38A8AEA/wcA///9f///////x/8BAP8HAP///X//////+//+AQD/////"
                + "//1///////F//AEA///////9f/////////8BAP+q+v///X//////////AQA/AOD///1//////////wEAH3zA///9f////7//7///"
                + "/x//wf///X////8f/8f///8f/8f///1///////v/////H//D///9f//////x/////x/+wP///X////////////8fKMD///1/6///"
                + "////////fwDw///9f4Hq+////////////////X8AwPH///////////////1/AMC////////////////9f3/AH///////////////"
                + "/X//wf////////////////1//4Gq///////////////9f/8BAP///////////////X//AQD///////////////1///8B////////"
                + "////////f+//B/7//////////////3////////////////////9/////////////////////f////////////////////3//////"
                + "//////////////9/////////////////////f////////////////////3////////////////////9/////////////////////"
                + "f////////////////////3////////////////////9/////////////////////f////////////////////3//////////////"
                + "//////9/////////////////////f////////////////////3////////////////////9/////v+//////////////f////wuC"
                + "/v///////////3/////wf/j///////////9///8/rKrh////////////f///HwMAxv///////////3///88BAJz///////////9/"
                + "///HwRoc////////////f///33Fw3P///////////3///89xcJz///////////9////H4T8c////////////f///n4EPnP//////"
                + "/////3///x8GBx/+//////////9///9/+P9/+P//////////f////6Gq/uH//////////3////8fR/iH//////////9////////h"
                + "H77q////////f///////h38Ix////////3///////x9+/N////////9///////9/6P/P////////f////////8H/x////////3//"
                + "///////H//f///////9/////////z//j////////f////////8P/gf///////3/////////wqh/+//////9///////8/PAR/+P//"
                + "////f///////Dw8f/uH//////3//////78LDf/iH+/7///9//////wP88P/hHyDo////f//////wf/z/h///h////3////9/vD7/"
                + "/x/+qhr+//9//////xwc//9/PABg/P//f////38cnP///xx8wPn//3////8//w///38cr8Hx//9/////D7/C/////QfB/f//f///"
                + "/8ML8f///7wHwfn//3/////w8P///38c/sDx//9///8/PPz/////GSjA/P//f7v6Dw///////2EAMPz//38A4cPD//////+H/w//"
                + "//9/qofw8P//////H6rC////fwD/P/z///////9x/P///38A/x////////////////9/EP7f////////////////fw/8n///////"
                + "/////////38D/B/+//////////////9/Aa9//P//////////////f/8D6vz//////////////3/qAUD8//////////////9/////"
                + "////////////////f////////////////////3////////////////////9/////////////////////f///////////////////"
                + "/3////////////////////9/////////////////////f////////////////////3////////////////////9/////////////"
                + "////////f////////////////////3////////////////////9/////////////////////f////////////////////3//////"
                + "//////////////9/////////////////////f////////////////////3////////////////////9/////////////////////"
                + "f////6uq/v///////////3////8BAPz///////////9///+/AQD8////////////f///DwEA/P///////////3///wcBAPz/////"
                + "//////9/////AQD8////////////f////wEA/P///////////3////8BAPz///////////9/////AQD8////////////f////x/A"
                + "/////////////3////////////////////9/////////////////////f////////////////////3////////////////////9/"
                + "////////////////////f////////////////////3////////////////////9/////////////////////f///////////////"
                + "/////3////////////////////9/////////////////////f//////////////v/////3//////////////h/////9/////////"
                + "/////wf/////f////////////////////3//////r/r///////////9//////wfw////v6rq////f/////8H8P///x8AwP///3//"
                + "////B/D///8PAMD///9//////wfw////BwDA////f////////////wcAwP///3+q//////////8HAMD///9/AP//////////BwDA"
                + "////fwD//////////x8AwP///38A//////////8fAMD///9/AP//////////////////fwD//////////////////38A////////"
                + "//////////9/AP//////////////////fwCq/////////////////38AAP////////////////9/AAD/////////////////fwAA"
                + "/////////////////38AAKqq//////////////9/AAAAAP///6+q6v//////fw=="));
        CHUNKS.put(key(51, 95), Base64.getDecoder().decode(
                "AP8HAP8HQMD/r/of8H/w/wD/BwD//////4fw//9/4H8A//////+v/v8X9P//f8B/AP+vqv/rAqj/B/Cr/3/AfwD/BwD/AQAA+4ew"
                + "APovgH8A/wMA7wAAgKD+HwDwBwB/AP8AAAeq8KEa/AsA8AeAKv8rAKAH8KAQEPyBAu9/AAD/AQAAB3EA4NGrAADHfwAA/wEAAB/x"
                + "8cHR8Qfw/38AAP8AAAAv4KDAwP0fsPp/AAA/AAAAB/BB8MH/HxrwKwAACwAAAAf6/////x8A8AEAAAEAAPhroP//v/kPAPABAAAB"
                + "QHDwAQCq+h+gAgTwAQAAAQAw8AEAAPAf4AAA8EcAAAcAEP8LIADwHwAAAOh/AAAHAPD/AQDw/x8AfADAfwAABwDw/0EA8Pr/B6wB"
                + "wH8AAP//v/4rsH/w//8HB8AvAAC/6h/8AQp68P+vBgPABwAAH8Af/AEA8P+/AvwAwAcAAB/Aqv4AAKD6HwAoAOh/AAAfYCx8AEAA"
                + "8B8AAADAfwAAC0EGfwBAAPAfAKr+wC8AAAHBvv8DgMD6H/AHOPAHAAABeij+QRBA8H/8HwDwBwAAH2AAvAAAaPD//58B+ysAAP//"
                + "/x/EH8H/6/8fAPEBAAD///8f/PrB/4D4DwD/AQAAv7/r/3/w/ysAoAL4/wEAAB8Owf9/8P+gAAAA8P9/AAAfHMH/7796AMAAAPDr"
                + "ewAAHx///4cKcAEsAMD/wWEAAB8f////A3AABACA/+DDAAAfCq/qvwBwAAQAEH/AAQAACx/HwR8M/8d/APD/8QEAAAHK9/d/B7/+"
                + "/wf/v+4BAAABwPf6/0cdvOoH/x/AAQAAAcAn4D8/HRyAx7/+oAAAAP//B4APCvgPAP8fPHAAAAD///8PhyuwBgD/GhwwAAAAv7//"
                + "AwfAH/zxP/C/AQAAAB8OrwEHgP/q/w/xGwEAAAAfPwfxegQvgP7/v+AAAAAAHx8DcXAAhwH8/Q8AAAAAAB8LgWAwAAcB/7gCAAAA"
                + "AAAfAAGADwAHAH8AAAAAAAAA/+sKAALABwB/AQAAAAAAAP9BgAYAwQPA/wEAAAAAAAD/QQDs//oBwP8BAAAAAAAA/38AxH/w8cH/"
                + "CwAAAAAAQP////9/8P+/+gEAAAAAAH//////////H/ABAAAAAAF////7///7/x/oAQAAAAD/f6qqoKqqoKqqgAYAAADAr2oAAAAA"
                + "AAAAAAAEAAAA8AMAAAAAAAAAAADwBwAAAPAAAAcAAAgAAAwAoAIAAAB8AAAPAAAAAEAAAAAAAAAAfAAAB0DAAYB7AAAAAAAAAD8A"
                + "AAfwwQEQcAB8AAAAAAALAAAH+/EBcHAAeAAAAAAAAQAAA/H+ACrwAfAgAAAAwAEAAMf/B/z//////////////3///wf8////////"
                + "//////9////H///////////////9f///h/7//////////////X/v/wf8//////////////1/x/8H/P+q6qrq///////9f///A/9/"
                + "AEAAwP///////X//qwD/fwBAAMD///////1//wEA/38AQADA///////9f/8BAP9/AEAAwP///////X//wf//fwBAAMD///////1/"
                + "/8C//38AQADA///////9fyrAH////////////////X8AwPv///////////////1/AcDx///////////////9f8H///////////+q"
                + "+v///X////////////8/AOD///1///////v/////H3zA///9f//////x/////x//wf///X////+//+////8f/8f///1/////H//H"
                + "////H//D///9f/+/qv6//v///x/+wP///X//HwD8H/z///8fKMD///1//x8A/B/8////fwDw///9f/8fAPz7//7//////////X//"
                + "HwD88X/8//////////1//x8A/P/v///////////9f/8fAPz/x////////////X//HwD8/////v////////1////////6f/z+////"
                + "///9f6+q/v9/8O9//P//////+X8HAPz/f/DH/////////+F/BwD8/////6////////+HfwcA/P////8H////////H38HAKjqv6r+"
                + "B////////39/BwAAwB8A/P////////9/fwcAAMAfAPz/////////P3///wHAHwD8/////////w9///8BwB8A/P/////////Df///"
                + "AcAfAPz///////+r8H///wHAHwD8////////8P9///8BgAoA/P//////P/z/f6+q/gMA/v///////w///38HAPzBH/z////////D"
                + "//9/BwD88X/8////////8P//fwcA/PF//P//////P/z//38HAPzhP/z//////w////9/BwD8gQ/8///////D////fwcA/AEC/P//"
                + "////8P///3////8HAP//////P/z///9//////////////w//////f//////////////D/////3//////////////8P////9/////"
                + "////////f/z/////f////////////3///////3////////////9///////9/r6qqqqqqqqr/P///////f////6uq//8f/g//////"
                + "/3////8BAP//f/jD//////9/////AQD/qvqh8P//////f////wEAfwDw/////////3////8BAH8A8P////////9/////AQB/APD/"
                + "////////f6qqqgAAKgCgqqqqqqqqqirCAUD+//////////////9/gLFD/P//////////////fwAfQP///////////////38AG0D+"
                + "//////////////9/wKFA/P//////////////f8IBQP7//////////////3//AUD8//////////////9/qwf//P//////////////"
                + "fwH+P/z//////////////38H/B////////////////9/Gvzf////////////////fwD/n////////////////38C/x/+////////"
                + "+/7///9/AK96+P//////vyDo////f6rD4eH//////w//h////38R8IeH///////Dqhr+//9///8fHv7/////MQBg/P//f///f3j4"
                + "/////xx8wPn//3/////hofv//38cr8Hx//9/////hx/g/////QfB/f//f////x//h////7wHwfn//3////9/vh7//38c/sDx//9/"
                + "////fxyc////HCjA/P//f/+/qv4cHP//P3wAMPz//3//HwB8+H/+/w///w////9//x8q/KF++P/Dv6rC////f/8fCvxH6OH/8A9x"
                + "/P///3//f8D//4eHP/zD//////9//3/A//8fHg7/8P//////f///////f3gAP/z//////3//6v/////h/wv///////9/f8D/////"
                + "x//B////////f3/E/////8//9////////39/xP/////D/+f///////9/f8T/////wP/H////////f3/A////P/z+3////////39/"
                + "wP///w9/qM////////9////////DPxzC////////f////7/v8A///////////3////8LAvzD//////////9/////8P//8P//////"
                + "////f///P6yvP/z//////////3///x8DBx7///////////9////PwR+c////////////f///x/F6HP///////////3///99xcNz/"
                + "//////////9////PYTCc////////////f///x4EKHP///////////3///58BAMz///////////9///8fBgDD////////////f///"
                + "f/j/8P///////////3////+hKvz///////////9/////H8f/////////////f////////////////////3//////////////////"
                + "//9/////////////////////f/////+q/////////////3////8rAP////////////9/////ARz/////////////f////7EO/6r6"
                + "/////////3////8RBCoA8P////////9/////EQQArPH/////////f///qr+qKgTh/////////3+qKgAPAAAEgaqqqqqqqqoqAAAA"
                + "AP///wcAwP//////fwAAAAD///8HAMD//////38AAAAA////BwDA//////9/AAAAAP///wcAwP//////fwAAAAD///8HAMD/////"
                + "/38AAAAA////AwDw//////9/AAAAAP///wEA8P//////fwAA//////8AAPD//////38AAP////8/AADw//////9/AAD/////DwAA"
                + "/P//////fwAA/////wMAAPz//////38A//////8AAAD8//////9/AP////8/AAAA/P//////fwD/////CwAAAP///////38A////"
                + "/wAAAPD///////9/AP///z8AAAD8v6rq////fwD///8PAAAA/x8AwP///38A////AwAAwP8PAMD///9/AP///wAAAPD/BwDA////"
                + "f////38AAAD8/wcAwP///3////9/AAAA/P8HAMD///9/////PwAAAP//BwDA////f////w8AAMD//x8AwP///3////8DAAD8//8f"
                + "AMD///9/////AAAA/P//////////f///fwAAAP///////////3///38AAAD///////////9///8/AADA////////////f///DwAA"
                + "8P///////////3///wIAAP////////////9//y8AAAD/////////////f/8CAADA/////////////38/AAAA8P////////////9/"
                + "CwAAAP//////////////fwAAAPD//////////////38AAADw//////////////9/AAAA/P//////////////fwAAAP//////////"
                + "/////38AAPD///////////////9/AAD8////////////////fwDA/7/q/////////////38A//8LgP7///////////9/////AQD8"
                + "////////////f////wEA/P///////////3////8BAPz///////////9/////AQD8////////////f////wEA/P///////////3//"
                + "//8BAPz///////////9/////AQD8////////////f////wEA/P///////////3////////////////////9/////////////////"
                + "////f////////////////////3////////////////////9/////////////////////f////////////////////3//////////"
                + "//////////9/////q6r/////////////f////wEA/////////////3////8BAP+q+v////////9/////AQB/APD/////////f///"
                + "/wEAfwDw/////////3////8BAH8A8P////////9/qqqqAAAqAKCqqqqqqqqqKg=="));
    }

    private DroElfPrifCollisionMap() {}

    static boolean supports(WorldPoint point) {
        if (point == null || point.getPlane() < 0 || point.getPlane() > 3) return false;
        return CHUNKS.containsKey(key(point.getX() >> 6, point.getY() >> 6));
    }

    static boolean isInsideEmbeddedArea(WorldPoint point) {
        return point != null
                && point.getX() >= MIN_X && point.getX() <= MAX_X
                && point.getY() >= MIN_Y && point.getY() <= MAX_Y
                && point.getPlane() >= 0 && point.getPlane() <= 3;
    }

    static boolean canStep(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) return false;

        final int dx = to.getX() - from.getX();
        final int dy = to.getY() - from.getY();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) return false;

        final int x = from.getX();
        final int y = from.getY();
        final int p = from.getPlane();

        // Cardinal movement. These four expressions mirror Efficient Walker's
        // directional wrappers around the two stored edge bits.
        if (dy == 0) {
            return dx < 0 ? eastEdgeOpen(x - 1, y, p) : eastEdgeOpen(x, y, p);
        }
        if (dx == 0) {
            return dy < 0 ? northEdgeOpen(x, y - 1, p) : northEdgeOpen(x, y, p);
        }

        // Diagonal movement uses all four bounding cardinal edges. This prevents
        // corner-cutting and matches the supplied walker's diagonal rules.
        if (dx < 0 && dy < 0) {
            return northEdgeOpen(x, y - 1, p)
                    && eastEdgeOpen(x - 1, y - 1, p)
                    && eastEdgeOpen(x - 1, y, p)
                    && northEdgeOpen(x - 1, y - 1, p);
        }
        if (dx < 0) { // north-west
            return northEdgeOpen(x, y, p)
                    && eastEdgeOpen(x - 1, y + 1, p)
                    && eastEdgeOpen(x - 1, y, p)
                    && northEdgeOpen(x - 1, y, p);
        }
        if (dy < 0) { // south-east
            return northEdgeOpen(x, y - 1, p)
                    && eastEdgeOpen(x, y - 1, p)
                    && eastEdgeOpen(x, y, p)
                    && northEdgeOpen(x + 1, y - 1, p);
        }
        // north-east
        return northEdgeOpen(x, y, p)
                && eastEdgeOpen(x, y + 1, p)
                && eastEdgeOpen(x, y, p)
                && northEdgeOpen(x + 1, y, p);
    }

    static boolean hasAnyExit(WorldPoint point) {
        if (!supports(point)) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                if (canStep(point, new WorldPoint(point.getX() + dx, point.getY() + dy, point.getPlane()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean northEdgeOpen(int x, int y, int plane) {
        return edgeOpen(x, y, plane, 0);
    }

    private static boolean eastEdgeOpen(int x, int y, int plane) {
        return edgeOpen(x, y, plane, 1);
    }

    private static boolean edgeOpen(int x, int y, int plane, int edge) {
        if (plane < 0 || plane > 3) return false;
        final byte[] chunk = CHUNKS.get(key(x >> 6, y >> 6));
        if (chunk == null) return false;

        final int localX = x & 63;
        final int localY = y & 63;
        final int bitIndex = ((((plane * 64) + localY) * 64 + localX) * 2) + edge;
        final int byteIndex = bitIndex >>> 3;
        if (byteIndex < 0 || byteIndex >= chunk.length) return false;

        return ((chunk[byteIndex] >>> (bitIndex & 7)) & 1) != 0;
    }

    private static int key(int regionX, int regionY) {
        return (regionX << 16) ^ (regionY & 0xFFFF);
    }
}
