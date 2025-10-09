# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn java.awt.Color

# Apache POI library rules
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }
-keepclassmembers class org.apache.poi.** { *; }

# Keep HSSFWorkbook and related classes
-keep class org.apache.poi.hssf.usermodel.HSSFWorkbook { *; }
-keep class org.apache.poi.ss.usermodel.CellType { *; }
-keep class org.apache.poi.hssf.** { *; }
-keep class org.apache.poi.ss.** { *; }

# Keep all POI model classes
-keep class * extends org.apache.poi.** { *; }
-keepclassmembers class * extends org.apache.poi.** { *; }

# R8 generated missing rules
-dontwarn org.etsi.uri.x01903.v13.SignaturePolicyIdType
-dontwarn org.etsi.uri.x01903.v13.SignedDataObjectPropertiesType
-dontwarn org.etsi.uri.x01903.v13.SignerRoleType
-dontwarn org.openxmlformats.schemas.drawingml.x2006.chart.CTChartLines
-dontwarn org.openxmlformats.schemas.drawingml.x2006.main.CTEffectContainer
-dontwarn org.openxmlformats.schemas.drawingml.x2006.main.CTTableStyleTextStyle
-dontwarn org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSignedTwipsMeasure