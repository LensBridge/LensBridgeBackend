package com.ibrasoft.lensbridge.frontend;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.springframework.security.access.annotation.Secured;

@Route("admin")
@Secured("ROLE_ADMIN")
//@CssImport("./styles/shared-styles.css")
public class AdminDashboardView extends VerticalLayout {

    private final TextField eventNameField = new TextField("Event Name");
    private final Button createEventButton = new Button("Create Event");
    private final NativeLabel statusLabel = new NativeLabel();

    public AdminDashboardView() {
        add(eventNameField, createEventButton, statusLabel);

        createEventButton.addClickListener(event -> {
            // Here you would make a REST call to /api/admin/create-event.
            statusLabel.setText("Event '" + eventNameField.getValue() + "' created.");
        });
    }
}
