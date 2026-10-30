package fr.f3n.tiroir;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Widget « Post-it numérique » : les 1 à 3 éléments les plus urgents, avec leur pastille. Un tap ouvre l'overlay de l'élément. */
public class Postit extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, vue(c));
    }

    /** Rafraîchit tous les widgets posés (appelé à chaque modification et à l'ouverture de l'appli). */
    static void maj(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, Postit.class));
            if (ids.length == 0) return;
            RemoteViews v = vue(c);
            for (int id : ids) m.updateAppWidget(id, v);
        } catch (Exception e) { /* un widget ne doit jamais faire planter l'appli */ }
    }

    private static int couleur(int al) { return al == 2 ? 0xFFE53935 : al == 1 ? 0xFFFB8C00 : 0xFF4CAF50; }

    private static RemoteViews vue(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), c.getResources().getIdentifier("postit", "layout", c.getPackageName()));
        LocalDate auj = LocalDate.now();
        List<Item> l = new ArrayList<>(Donnees.get(c).items);
        l.sort((a, b) -> Double.compare(a.score(auj), b.score(auj)));
        int[] rows = {id(c, "r1"), id(c, "r2"), id(c, "r3")}, dots = {id(c, "d1"), id(c, "d2"), id(c, "d3")}, ts = {id(c, "t1"), id(c, "t2"), id(c, "t3")};
        PendingIntent ouvrir = PendingIntent.getActivity(c, 900, new Intent(c, Principal.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(id(c, "fond"), ouvrir);
        v.setOnClickPendingIntent(id(c, "titre"), ouvrir);
        for (int i = 0; i < 3; i++) {
            if (i < l.size()) {
                Item it = l.get(i);
                v.setViewVisibility(rows[i], View.VISIBLE);
                v.setTextColor(dots[i], couleur(it.alerte(auj)));
                v.setTextViewText(ts[i], it.emoji() + " " + it.nom + " · " + it.court(auj));
                v.setOnClickPendingIntent(rows[i], PendingIntent.getActivity(c, 910 + it.id, new Intent(c, Principal.class)
                        .putExtra("overlay", it.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else if (i == 0) {
                v.setViewVisibility(rows[i], View.VISIBLE);
                v.setTextColor(dots[i], 0xFF4CAF50);
                v.setTextViewText(ts[i], "Rien à signaler");
            } else v.setViewVisibility(rows[i], View.GONE);
        }
        return v;
    }

    private static int id(Context c, String nom) { return c.getResources().getIdentifier(nom, "id", c.getPackageName()); }
}
