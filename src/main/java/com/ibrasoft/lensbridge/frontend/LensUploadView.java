package com.ibrasoft.lensbridge.frontend;

import com.ibrasoft.lensbridge.exception.VideoTooLongException;
import com.ibrasoft.lensbridge.frontend.config.UploadConfig;
import com.ibrasoft.lensbridge.service.CloudinaryService;
import com.ibrasoft.lensbridge.service.VideoProcessingService;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;

import java.io.File;
import java.io.InputStream;

@Route("upload")
@Secured("ROLE_USER")
@CssImport("./styles/shared-styles.css")
public class LensUploadView extends VerticalLayout {

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private VideoProcessingService videoProcessingService;

    public LensUploadView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("📸 Upload to LensBridge");
        title.getStyle().set("margin-bottom", "0.5rem");

        Paragraph subtitle = new Paragraph("Images and short videos only. Maximum of 1.5 minutes for videos.");
        subtitle.getStyle().set("color", "#666");

        HorizontalLayout header = new HorizontalLayout(title, subtitle);
        header.setAlignItems(Alignment.BASELINE);
        header.setSpacing(true);

        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();

        UploadConfig config = new UploadConfig();

        config.getAddFiles().setOne("Upload your media coverage here!");
        config.getDropFiles().setOne("Drop your files here!");

        config.getError().setFileIsTooBig("File is too big!");
        config.getError().setIncorrectFileType("File type not allowed!");

        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(
                "image/jpeg", "image/png", "image/webp",
                "video/mp4", "video/quicktime", "video/x-matroska"
        );
        upload.setMaxFiles(10);
        upload.setWidth("100%");
        upload.getStyle().set("max-width", "500px");
        upload.setI18n(config);
        Div previewContainer = new Div();
        previewContainer.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "1rem")
                .set("margin-top", "1.5rem");

        upload.addSucceededListener(event -> {
            for (String fileName : buffer.getFiles()) {
                InputStream inputStream = buffer.getInputStream(fileName);
                String mimeType = buffer.getFileData(fileName).getMimeType();
                previewContainer.removeAll();

                try {
                    if (mimeType.startsWith("image")) {
                        byte[] imageBytes = inputStream.readAllBytes();
                        String url = cloudinaryService.uploadImage(imageBytes, fileName);

                        Image img = new Image(url, fileName);
                        img.setWidth("300px");
                        img.getStyle().set("border-radius", "8px").set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)");
                        previewContainer.add(img);

                    } else if (mimeType.startsWith("video")) {
                        File transcoded = videoProcessingService.transcodeToHevc(inputStream, fileName);
                        String url = cloudinaryService.uploadVideo(transcoded, fileName);
                        transcoded.delete();

                        Video video = new Video(url);
                        video.setWidth("400px");
//                        video.setAutoplay(false);
//                        video.setControls(true);
                        video.getStyle().set("border-radius", "8px").set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)");
                        previewContainer.add(video);

                    } else {
                        Notification.show("❌ Unsupported file type: " + fileName);
                    }

                } catch (VideoTooLongException e) {
                    Notification.show("⚠️ " + fileName + " is too long! Max 1.5 minutes.");

                } catch (Exception e) {
                    e.printStackTrace();
                    Notification.show("❌ Failed to upload " + fileName + ": " + e.getMessage());
                }
            }
        });

        add(header, subtitle, upload, previewContainer);
    }
}
