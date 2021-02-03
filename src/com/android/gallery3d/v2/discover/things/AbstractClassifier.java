package com.android.gallery3d.v2.discover.things;

import android.net.Uri;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.sprd.frameworks.StandardFrameworks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public abstract class AbstractClassifier {
    private static final String TAG = AbstractClassifier.class.getSimpleName();

    private static final float THRESHOLD = 0.7f;
    private static final float HIGH_THRESHOLD = 0.8f;

    public static final int TF_UNKNOWN = -1;

    private static final int TF_AIRCRAFT = 0;
    private static final int TF_ANIMAL = 1;
    private static final int TF_BABY = 2;
    private static final int TF_BASKETBALL = 3;
    private static final int TF_BEDROOM = 4;
    private static final int TF_BICYCLE = 5;
    private static final int TF_BIRD = 6;
    private static final int TF_BRIDGE = 7;
    private static final int TF_BUILDING = 8;
    private static final int TF_BUS = 9;
    private static final int TF_CAR = 10;
    private static final int TF_CAT = 11;
    private static final int TF_CLASSROOM = 12;
    private static final int TF_CONFERENCE_ROOM = 13;
    private static final int TF_CRAWLING_ANIMAL = 14;
    private static final int TF_DESKTOP_COMPUTER = 15;
    private static final int TF_DOCUMENT = 16;
    private static final int TF_DOG = 17;
    private static final int TF_ELECTRIC_BICYCLE = 18;
    private static final int TF_EVERGREEN = 19;
    private static final int TF_FARMLAND = 20;
    private static final int TF_FISH = 21;
    private static final int TF_FLOWER = 22;
    private static final int TF_FOOD = 23;
    private static final int TF_FOOTBALL = 24;
    private static final int TF_GRADUATION_PHOTO = 25;
    private static final int TF_GRASSLAND = 26;
    private static final int TF_GROUP_PHOTO = 27;
    private static final int TF_HELICOPTER = 28;
    private static final int TF_KITCHEN = 29;
    private static final int TF_LAPTOP = 30;
    private static final int TF_METRO = 31;
    private static final int TF_MOBILE_PHONE = 32;
    private static final int TF_MOTORCYCLE = 33;
    private static final int TF_MOUNTAIN = 34;
    private static final int TF_OFFICE = 35;
    private static final int TF_PARLOUR = 36;
    private static final int TF_PARTY = 37;
    private static final int TF_PEOPLE = 38;
    private static final int TF_PLAYGROUND = 39;
    private static final int TF_RIVER = 40;
    private static final int TF_ROAD = 41;
    private static final int TF_SEA = 42;
    private static final int TF_SELFIE = 43;
    private static final int TF_SHIP = 44;
    private static final int TF_SHOPPING_MALL = 45;
    private static final int TF_SKY = 46;
    private static final int TF_TELEPHONE = 47;
    private static final int TF_TRAIN = 48;
    private static final int TF_TREE = 49;
    private static final int TF_WAITING_ROOM = 50;
    private static final int TF_NIGHT_SCENE = 51;
    private static final int TF_CAKE = 52;
    private static final int TF_LAKE = 53;
    private static final int TF_CERTIFICATE_CARD = 54;

    private static final int TF_INDOOR = 500;
    private static final int TF_COMPUTER = 501;
    private static final int TF_SPORT = 502;
    private static final int TF_SEA_RIVER = 503;
    private static final int TF_PHONE = 504;

    private static final LinkedHashMap<Integer, Integer> sNameResId = new LinkedHashMap<>();

    static {
        sNameResId.put(TF_UNKNOWN, R.string.tf_unknown);
        sNameResId.put(TF_AIRCRAFT, R.string.tf_aircraft);
        sNameResId.put(TF_ANIMAL, R.string.tf_animal);
        sNameResId.put(TF_BABY, R.string.tf_baby);
        sNameResId.put(TF_BASKETBALL, R.string.tf_basketball);
        sNameResId.put(TF_BEDROOM, R.string.tf_bedroom);
        sNameResId.put(TF_BICYCLE, R.string.tf_bicycle);
        sNameResId.put(TF_BIRD, R.string.tf_bird);
        sNameResId.put(TF_BRIDGE, R.string.tf_bridge);
        sNameResId.put(TF_BUILDING, R.string.tf_building);
        sNameResId.put(TF_BUS, R.string.tf_bus);
        sNameResId.put(TF_CAR, R.string.tf_car);
        sNameResId.put(TF_CAT, R.string.tf_cat);
        sNameResId.put(TF_CLASSROOM, R.string.tf_classroom);
        sNameResId.put(TF_CONFERENCE_ROOM, R.string.tf_conference_room);
        sNameResId.put(TF_CRAWLING_ANIMAL, R.string.tf_crawling_animal);
        sNameResId.put(TF_DESKTOP_COMPUTER, R.string.tf_desktop_computer);
        sNameResId.put(TF_DOCUMENT, R.string.tf_document);
        sNameResId.put(TF_DOG, R.string.tf_dog);
        sNameResId.put(TF_ELECTRIC_BICYCLE, R.string.tf_electric_bicycle);
        sNameResId.put(TF_EVERGREEN, R.string.tf_evergreen);
        sNameResId.put(TF_FARMLAND, R.string.tf_farmland);
        sNameResId.put(TF_FISH, R.string.tf_fish);
        sNameResId.put(TF_FLOWER, R.string.tf_flower);
        sNameResId.put(TF_FOOD, R.string.tf_food);
        sNameResId.put(TF_FOOTBALL, R.string.tf_football);
        sNameResId.put(TF_GRADUATION_PHOTO, R.string.tf_graduation_photo);
        sNameResId.put(TF_GRASSLAND, R.string.tf_grassland);
        sNameResId.put(TF_GROUP_PHOTO, R.string.tf_group_photo);
        sNameResId.put(TF_HELICOPTER, R.string.tf_helicopter);
        sNameResId.put(TF_KITCHEN, R.string.tf_kitchen);
        sNameResId.put(TF_LAPTOP, R.string.tf_laptop);
        sNameResId.put(TF_METRO, R.string.tf_metro);
        sNameResId.put(TF_MOBILE_PHONE, R.string.tf_mobile_phone);
        sNameResId.put(TF_MOTORCYCLE, R.string.tf_motorcycle);
        sNameResId.put(TF_MOUNTAIN, R.string.tf_mountain);
        sNameResId.put(TF_OFFICE, R.string.tf_office);
        sNameResId.put(TF_PARLOUR, R.string.tf_parlour);
        sNameResId.put(TF_PARTY, R.string.tf_party);
        sNameResId.put(TF_PEOPLE, R.string.tf_people);
        sNameResId.put(TF_PLAYGROUND, R.string.tf_playground);
        sNameResId.put(TF_RIVER, R.string.tf_river);
        sNameResId.put(TF_ROAD, R.string.tf_road);
        sNameResId.put(TF_SEA, R.string.tf_sea);
        sNameResId.put(TF_SELFIE, R.string.tf_selfie);
        sNameResId.put(TF_SHIP, R.string.tf_ship);
        sNameResId.put(TF_SHOPPING_MALL, R.string.tf_shopping_mall);
        sNameResId.put(TF_SKY, R.string.tf_sky);
        sNameResId.put(TF_TELEPHONE, R.string.tf_telephone);
        sNameResId.put(TF_TRAIN, R.string.tf_train);
        sNameResId.put(TF_TREE, R.string.tf_tree);
        sNameResId.put(TF_WAITING_ROOM, R.string.tf_waiting_room);
        sNameResId.put(TF_NIGHT_SCENE, R.string.tf_night_scene);
        sNameResId.put(TF_CAKE, R.string.tf_cake);
        sNameResId.put(TF_LAKE, R.string.tf_lake);

        sNameResId.put(TF_INDOOR, R.string.tf_indoor);
        sNameResId.put(TF_COMPUTER, R.string.tf_computer);
        sNameResId.put(TF_SPORT, R.string.tf_sport);
        sNameResId.put(TF_SEA_RIVER, R.string.tf_sea_river);
        sNameResId.put(TF_PHONE, R.string.tf_phone);
    }

    private static final List<Integer> s_Group_People = new ArrayList<>();
    private static final List<Integer> s_Group_Sky = new ArrayList<>();
    private static final List<Integer> s_Group_Green_Plant = new ArrayList<>();
    private static final List<Integer> s_Group_River = new ArrayList<>();
    private static final List<Integer> s_Group_Animal = new ArrayList<>();
    private static final List<Integer> s_Group_Indoor = new ArrayList<>();
    private static final List<Integer> s_Group_Computer = new ArrayList<>();
    private static final List<Integer> s_Group_Motocycle = new ArrayList<>();
    private static final List<Integer> s_Group_Food = new ArrayList<>();
    private static final List<Integer> s_Group_Train = new ArrayList<>();
    private static final List<Integer> s_Group_Phone = new ArrayList<>();
    private static final List<Integer> s_Group_Document = new ArrayList<>();

    static {
        //
        s_Group_People.add(TF_BABY);
        s_Group_People.add(TF_GRADUATION_PHOTO);
        s_Group_People.add(TF_GROUP_PHOTO);
        s_Group_People.add(TF_PARTY);
        s_Group_People.add(TF_PEOPLE);
        s_Group_People.add(TF_SELFIE);
        //
        s_Group_Sky.add(TF_BUILDING);
        s_Group_Sky.add(TF_SKY);
        //
        s_Group_Green_Plant.add(TF_EVERGREEN);
        s_Group_Green_Plant.add(TF_FARMLAND);
        s_Group_Green_Plant.add(TF_FLOWER);
        s_Group_Green_Plant.add(TF_GRASSLAND);
        s_Group_Green_Plant.add(TF_TREE);
        //
        s_Group_River.add(TF_RIVER);
        s_Group_River.add(TF_LAKE);
        s_Group_River.add(TF_SEA);
        s_Group_River.add(TF_SHIP);
        s_Group_River.add(TF_BRIDGE);
        //
        s_Group_Animal.add(TF_ANIMAL);
        s_Group_Animal.add(TF_BIRD);
        s_Group_Animal.add(TF_CAT);
        s_Group_Animal.add(TF_CRAWLING_ANIMAL);
        s_Group_Animal.add(TF_DOG);
        s_Group_Animal.add(TF_FISH);
        //
        s_Group_Indoor.add(TF_CLASSROOM);
        s_Group_Indoor.add(TF_CONFERENCE_ROOM);
        s_Group_Indoor.add(TF_KITCHEN);
        s_Group_Indoor.add(TF_OFFICE);
        s_Group_Indoor.add(TF_PARLOUR);
        s_Group_Indoor.add(TF_WAITING_ROOM);
        s_Group_Indoor.add(TF_BEDROOM);
        //
        s_Group_Computer.add(TF_DESKTOP_COMPUTER);
        s_Group_Computer.add(TF_LAPTOP);
        //
        s_Group_Motocycle.add(TF_ELECTRIC_BICYCLE);
        s_Group_Motocycle.add(TF_MOTORCYCLE);
        //
        s_Group_Food.add(TF_FOOD);
        s_Group_Food.add(TF_CAKE);
        //
        s_Group_Train.add(TF_METRO);
        s_Group_Train.add(TF_TRAIN);
        //
        s_Group_Phone.add(TF_MOBILE_PHONE);
        s_Group_Phone.add(TF_TELEPHONE);
        //
        s_Group_Document.add(TF_DOCUMENT);
        s_Group_Document.add(TF_CERTIFICATE_CARD);
    }

    public abstract AbstractClassifier open();

    public abstract List<Recognition> recognize(String path, int orientation);

    public abstract List<Recognition> recognize(Uri uri, int orientation);

    public abstract void close();

    public static String recognizedThings(List<Recognition> list) {
        if (list.size() > 0) {
            StringBuilder builder = new StringBuilder();
            for (Recognition rn : list) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(rn.toString());
            }
            return builder.toString();
        }
        return "{}";
    }

    public static String getName(int classification) {
        Integer resId = sNameResId.get(classification);
        if (resId == null) {
            resId = sNameResId.get(TF_UNKNOWN);
        }
        return GalleryAppImpl.getApplication().getAndroidContext().getString(resId);
    }

    public static int getResult(List<Recognition> recognitionList) {
        int result = handleRecognition(recognitionList);
        //归类,合并
        //飞机
        if (result == TF_AIRCRAFT
                || result == TF_HELICOPTER) {
            return TF_AIRCRAFT;
        }
        //动物
        if (result == TF_ANIMAL
                || result == TF_CAT
                || result == TF_CRAWLING_ANIMAL
                || result == TF_DOG
                || result == TF_FISH
                || result == TF_BIRD) {
            return TF_ANIMAL;
        }
        //室内
        if (result == TF_CLASSROOM
                || result == TF_CONFERENCE_ROOM
                || result == TF_KITCHEN
                || result == TF_OFFICE
                || result == TF_PARLOUR
                || result == TF_WAITING_ROOM
                || result == TF_BEDROOM) {
            return TF_INDOOR;
        }
        //电脑
        if (result == TF_DESKTOP_COMPUTER
                || result == TF_LAPTOP) {
            return TF_COMPUTER;
        }
        //摩托车
        if (result == TF_ELECTRIC_BICYCLE
                || result == TF_MOTORCYCLE) {
            return TF_MOTORCYCLE;
        }
        //常绿植物
        if (result == TF_EVERGREEN
                || result == TF_FARMLAND
                || result == TF_GRASSLAND) {
            return TF_EVERGREEN;
        }
        //美食
        if (result == TF_FOOD
                || result == TF_CAKE) {
            return TF_FOOD;
        }
        //运动
        if (result == TF_FOOTBALL
                || result == TF_BASKETBALL) {
            return TF_SPORT;
        }
        //合照
        if (result == TF_GROUP_PHOTO
                || result == TF_GRADUATION_PHOTO) {
            return TF_GROUP_PHOTO;
        }
        //火车
        if (result == TF_METRO
                || result == TF_TRAIN) {
            return TF_TRAIN;
        }
        //大海,河流,湖泊
        if (result == TF_RIVER
                || result == TF_SEA
                || result == TF_LAKE) {
            return TF_SEA_RIVER;
        }
        //电话
        if (result == TF_MOBILE_PHONE
                || result == TF_TELEPHONE) {
            return TF_PHONE;
        }
        //文档
        if (result == TF_CERTIFICATE_CARD) {
            return TF_DOCUMENT;
        }
        //人物
        if (result == TF_BABY) {
            return TF_PEOPLE;
        }
        return result;
    }

    private static int handleRecognition(List<Recognition> recognitionList) {
        if (recognitionList.size() == 0) {
            return TF_UNKNOWN;
        }

        //高标准分类，第一个类别的概率需要达到HIGH_THRESHOLD阀值
        if (needHighThreshold(recognitionList.get(0).getId())
                && recognitionList.get(0).getConfidence() >= HIGH_THRESHOLD) {
            return recognitionList.get(0).getId();
            //低标准分类，如果第一个类别的概率大于THRESHOLD阀值, 直接返回第一个类别
        } else if (!needHighThreshold(recognitionList.get(0).getId())
                && recognitionList.get(0).getConfidence() >= THRESHOLD) {
            return recognitionList.get(0).getId();
            //如果仅有一种类别,又达不到设定阀值, 认为识别无效
        } else if (recognitionList.size() == 1) {
            return TF_UNKNOWN;
        } else {
            //如果前两个概率加起来达到设定阀值,返回第一个类别
            if (isSameOtherGroup(recognitionList.get(0).getId(), recognitionList.get(1).getId())) {
                float sum = recognitionList.get(0).getConfidence()
                        + recognitionList.get(1).getConfidence();
                if (sum >= THRESHOLD) {
                    return recognitionList.get(0).getId();
                }
            } else if (isContainsPeople(recognitionList.get(0).getId(),
                    recognitionList.get(0).getConfidence(),
                    recognitionList.get(1).getId(),
                    recognitionList.get(1).getConfidence())) {
                float sum = recognitionList.get(0).getConfidence()
                        + recognitionList.get(1).getConfidence();
                if (sum >= THRESHOLD) {
                    return TF_PEOPLE;
                }
            }
            //判定所有得到的类别
            //是否同属于人物类
            boolean groupPeople = true;
            for (Recognition r : recognitionList) {
                if (s_Group_People.contains(r.getId())) {
                } else {
                    groupPeople = false;
                    break;
                }
            }
            if (groupPeople) {
                return TF_PEOPLE;
            }
            //是否同属于天空,建筑
            boolean groupSky = true;
            for (Recognition r : recognitionList) {
                if (s_Group_Sky.contains(r.getId())) {
                } else {
                    groupSky = false;
                    break;
                }
            }
            if (groupSky) {
                return recognitionList.get(0).getId();
            }
            //是否同属于植物类
            boolean groupPlant = true;
            for (Recognition r : recognitionList) {
                if (s_Group_Green_Plant.contains(r.getId())) {
                    continue;
                } else {
                    groupPlant = false;
                    break;
                }
            }
            if (groupPlant) {
                return recognitionList.get(0).getId();
            }
            //是否同属于河流类
            boolean groupRiver = true;
            for (Recognition r : recognitionList) {
                if (s_Group_River.contains(r.getId())) {
                    continue;
                } else {
                    groupRiver = false;
                    break;
                }
            }
            if (groupRiver) {
                return recognitionList.get(0).getId();
            }
            //是否同属于动物类
            boolean groupAnimal = true;
            for (Recognition r : recognitionList) {
                if (s_Group_Animal.contains(r.getId())) {
                    continue;
                } else {
                    groupAnimal = false;
                    break;
                }
            }
            if (groupAnimal) {
                return TF_ANIMAL;
            }
            //啥都不是,认为识别无效
            return TF_UNKNOWN;
        }
    }

    private static boolean needHighThreshold(int id) {
        return (id == TF_SELFIE)
                || (id == TF_PARTY);
    }

    private static boolean isContainsPeople(int id1, float confidence1, int id2, float confidence2) {
        boolean result = false;
        float peopleThreshold = 0.3f;

        if (((s_Group_People.contains(id1) && confidence1 >= peopleThreshold)
                || (s_Group_People.contains(id2) && confidence2 >= peopleThreshold))) {
            result = true;
        }
        return result;
    }

    private static boolean isSameOtherGroup(int id1, int id2) {
        boolean result = false;

        if ((s_Group_Sky.contains(id1) && s_Group_Sky.contains(id2))
                || (s_Group_Green_Plant.contains(id1) && s_Group_Green_Plant.contains(id2))
                || (s_Group_River.contains(id1) && s_Group_River.contains(id2))
                || (s_Group_Animal.contains(id1) && s_Group_Animal.contains(id2))
                || (s_Group_Indoor.contains(id1) && s_Group_Indoor.contains(id2))
                || (s_Group_Computer.contains(id1) && s_Group_Computer.contains(id2))
                || (s_Group_Motocycle.contains(id1) && s_Group_Motocycle.contains(id2))
                || (s_Group_Food.contains(id1) && s_Group_Food.contains(id2))
                || (s_Group_Train.contains(id1) && s_Group_Train.contains(id2))
                || (s_Group_Phone.contains(id1) && s_Group_Phone.contains(id2))
                || (s_Group_Document.contains(id1) && s_Group_Document.contains(id2))) {
            result = true;
        }
        return result;
    }
}
