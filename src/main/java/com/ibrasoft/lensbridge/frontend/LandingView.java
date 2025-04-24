package com.ibrasoft.lensbridge.frontend;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;

//import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("LensBridge | Connect Your Vision")
public class LandingView extends VerticalLayout {

    public LandingView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "#f9f9f9");

        add(
                createHeroSection(),
                createFeaturesSection(),
                createScreenshotSection(),
                createFooter()
        );
    }

    private Component createHeroSection() {
        VerticalLayout hero = new VerticalLayout();
        hero.setAlignItems(Alignment.CENTER);
        hero.setPadding(true);
        hero.setSpacing(false);
        hero.getStyle().set("background", "linear-gradient(135deg, #6e8efb, #a777e3)");
        hero.setWidthFull();
        hero.setHeight("50vh");

        H1 title = new H1("LensBridge");
        title.getStyle().set("color", "white").set("font-size", "3rem");

        Paragraph tagline = new Paragraph("Connecting your lens to the world with a single click.");
        tagline.getStyle().set("color", "white").set("font-size", "1.25rem");

        Button cta = new Button("Get Started");
        cta.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);

        hero.add(title, tagline, cta);
        return hero;
    }

    private Component createFeaturesSection() {
        HorizontalLayout features = new HorizontalLayout();
        features.setWidthFull();
        features.setPadding(true);
        features.setJustifyContentMode(JustifyContentMode.EVENLY);
        features.setWrap(true);

        features.add(
                createFeature("📸", "Seamless Camera Integration", "Connect and stream from any device."),
                createFeature("🔒", "Secure & Private", "End-to-end encryption for peace of mind."),
                createFeature("⚡", "Blazing Fast", "Experience real-time video with minimal delay.")
        );

        return features;
    }

    private Component createFeature(String icon, String title, String description) {
        VerticalLayout feature = new VerticalLayout();
        feature.setAlignItems(Alignment.CENTER);

        H2 featureTitle = new H2(icon + " " + title);
        Paragraph desc = new Paragraph(description);
        desc.getStyle().set("text-align", "center").set("max-width", "200px");

        feature.add(featureTitle, desc);
        return feature;
    }

    private Component createScreenshotSection() {
        VerticalLayout screenshots = new VerticalLayout();
        screenshots.setWidthFull();
        screenshots.setAlignItems(Alignment.CENTER);
        screenshots.setPadding(true);

        H2 sectionTitle = new H2("Preview LensBridge in Action");
        Image mockup = new Image("images/mockup.png", "App screenshot");
        mockup.setWidth("80%");
        mockup.getStyle().set("box-shadow", "0 4px 20px rgba(0, 0, 0, 0.1)");

        screenshots.add(sectionTitle, mockup);
        return screenshots;
    }

    private Component createFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.CENTER);
        footer.getStyle().set("background", "#eee").set("padding", "1em");

        Paragraph copyright = new Paragraph("© 2025 LensBridge. All rights reserved.");
        footer.add(copyright);

        return footer;
    }
}

