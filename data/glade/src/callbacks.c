#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include <gtk/gtk.h>

#include "callbacks.h"
#include "interface.h"
#include "support.h"


void
on_about1_activate                     (GtkMenuItem     *menuitem,
                                        gpointer         user_data)
{

}


void
on_searchName_changed                  (GtkEditable     *editable,
                                        gpointer         user_data)
{

}


void
on_saveSearchButton_clicked            (GtkButton       *button,
                                        gpointer         user_data)
{

}


void
on_startSearch_clicked                 (GtkButton       *button,
                                        gpointer         user_data)
{

}


void
on_searchResults_selection_changed     (GtkIconView     *iconview,
                                        gpointer         user_data)
{

}


void
on_stopSearch_clicked                  (GtkButton       *button,
                                        gpointer         user_data)
{

}


void
on_clearSearch_clicked                 (GtkButton       *button,
                                        gpointer         user_data)
{

}


gboolean
on_selectedResult_expose_event         (GtkWidget       *widget,
                                        GdkEventExpose  *event,
                                        gpointer         user_data)
{

  return FALSE;
}


gboolean
on_selectedResult_button_press_event   (GtkWidget       *widget,
                                        GdkEventButton  *event,
                                        gpointer         user_data)
{

  return FALSE;
}


gboolean
on_selectedResult_button_release_event (GtkWidget       *widget,
                                        GdkEventButton  *event,
                                        gpointer         user_data)
{

  return FALSE;
}


gboolean
on_selectedResult_motion_notify_event  (GtkWidget       *widget,
                                        GdkEventMotion  *event,
                                        gpointer         user_data)
{

  return FALSE;
}


gboolean
on_selectedResult_configure_event      (GtkWidget       *widget,
                                        GdkEventConfigure *event,
                                        gpointer         user_data)
{

  return FALSE;
}


gboolean
on_selectedResult_enter_notify_event   (GtkWidget       *widget,
                                        GdkEventCrossing *event,
                                        gpointer         user_data)
{

  return FALSE;
}


gboolean
on_selectedResult_leave_notify_event   (GtkWidget       *widget,
                                        GdkEventCrossing *event,
                                        gpointer         user_data)
{

  return FALSE;
}

