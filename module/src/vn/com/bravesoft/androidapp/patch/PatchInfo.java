package vn.com.bravesoft.androidapp.patch;

/**
 * 設定ダイアログのフッタに表示する domico-patch のメタ情報。
 *
 * <p>公式アプリの dex とは別の {@code classesN.dex} に入るが、参照元
 * ({@code PatchSettingsDialog}) からは {@code sget-object} で読まれるため
 * クロス dex 参照として実行時に解決される。
 */
public final class PatchInfo {

    public static final String CREDIT = "Domico-Patch — 非公式 UI/UX,プライバシー改善パッチ";

    public static final String COPYRIGHT = "© 2026 Fuku856";

    /**
     * パッチ版の表示文字列。実体はビルド時生成の {@link PatchBuildInfo} にあり、
     * javac が定数畳み込みするのでこのフィールドにも同じ値が焼き込まれる。
     */
    public static final String VERSION = PatchBuildInfo.VERSION;

    private PatchInfo() {
    }
}
