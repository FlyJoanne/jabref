package org.jabref.gui.preferences.network;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.stage.FileChooser;

import org.jabref.gui.DialogService;
import org.jabref.gui.preferences.PreferenceTabViewModel;
import org.jabref.gui.util.FileDialogConfiguration;
import org.jabref.logic.InternalPreferences;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.net.ProxyPreferences;
import org.jabref.logic.net.ProxyRegisterer;
import org.jabref.logic.net.URLDownload;
import org.jabref.logic.net.ssl.SSLCertificate;
import org.jabref.logic.net.ssl.TrustStoreManager;
import org.jabref.logic.os.OS;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.StandardFileType;
import org.jabref.model.strings.StringUtil;

import de.saxsys.mvvmfx.utils.validation.CompositeValidator;
import de.saxsys.mvvmfx.utils.validation.FunctionBasedValidator;
import de.saxsys.mvvmfx.utils.validation.ValidationMessage;
import de.saxsys.mvvmfx.utils.validation.ValidationStatus;
import de.saxsys.mvvmfx.utils.validation.Validator;
import kong.unirest.core.UnirestException;

public class NetworkTabViewModel implements PreferenceTabViewModel {
    private final BooleanProperty versionCheckProperty = new SimpleBooleanProperty();
    private final BooleanProperty proxyUseProperty = new SimpleBooleanProperty();
    private final StringProperty proxyHostnameProperty = new SimpleStringProperty("");
    private final StringProperty proxyPortProperty = new SimpleStringProperty("");
    private final BooleanProperty proxyUseAuthenticationProperty = new SimpleBooleanProperty();
    private final StringProperty proxyUsernameProperty = new SimpleStringProperty("");
    private final StringProperty proxyPasswordProperty = new SimpleStringProperty("");
    private final BooleanProperty proxyPersistPasswordProperty = new SimpleBooleanProperty();
    private final BooleanProperty passwordPersistAvailable = new SimpleBooleanProperty();
    private final ListProperty<CustomCertificateViewModel> customCertificateListProperty = new SimpleListProperty<>(FXCollections.observableArrayList());

    private final Validator proxyHostnameValidator;
    private final Validator proxyPortValidator;
    private final Validator proxyUsernameValidator;
    private final Validator proxyPasswordValidator;

    private final DialogService dialogService;
    private final CliPreferences preferences;


    private final ProxyPreferences proxyPreferences;
    private final ProxyPreferences backupProxyPreferences;
    private final InternalPreferences internalPreferences;

    private final TrustStoreManager trustStoreManager;

    private final AtomicBoolean sslCertificatesChanged = new AtomicBoolean(false);

    public NetworkTabViewModel(DialogService dialogService,
                               CliPreferences preferences) {
        this.dialogService = dialogService;
        this.preferences = preferences;
        this.proxyPreferences = preferences.getProxyPreferences();
        this.internalPreferences = preferences.getInternalPreferences();

        backupProxyPreferences = new ProxyPreferences(
                proxyPreferences.shouldUseProxy(),
                proxyPreferences.getHostname(),
                proxyPreferences.getPort(),
                proxyPreferences.shouldUseAuthentication(),
                proxyPreferences.getUsername(),
                proxyPreferences.getPassword(),
                proxyPreferences.shouldPersistPassword());

        proxyHostnameValidator = new FunctionBasedValidator<>(
                proxyHostnameProperty,
                input -> !StringUtil.isNullOrEmpty(input),
                ValidationMessage.error("%s > %s %n %n %s".formatted(
                        Localization.lang("Network"),
                        Localization.lang("Proxy configuration"),
                        Localization.lang("Please specify a hostname"))));

        proxyPortValidator = new FunctionBasedValidator<>(
                proxyPortProperty,
                input -> getPortAsInt(input).isPresent(),
                ValidationMessage.error("%s > %s %n %n %s".formatted(
                        Localization.lang("Network"),
                        Localization.lang("Proxy configuration"),
                        Localization.lang("Please specify a port"))));

        proxyUsernameValidator = new FunctionBasedValidator<>(
                proxyUsernameProperty,
                input -> !StringUtil.isNullOrEmpty(input),
                ValidationMessage.error("%s > %s %n %n %s".formatted(
                        Localization.lang("Network"),
                        Localization.lang("Proxy configuration"),
                        Localization.lang("Please specify a username"))));

        proxyPasswordValidator = new FunctionBasedValidator<>(
                proxyPasswordProperty,
                input -> !input.isBlank(),
                ValidationMessage.error("%s > %s %n %n %s".formatted(
                        Localization.lang("Network"),
                        Localization.lang("Proxy configuration"),
                        Localization.lang("Please specify a password"))));

        this.trustStoreManager = new TrustStoreManager(Path.of(preferences.getSSLPreferences().getTruststorePath()));
    }

    @Override
    public void setValues() {
        versionCheckProperty.setValue(internalPreferences.isVersionCheckEnabled());

        setProxyValues();
        setSSLValues();
    }

    private void setProxyValues() {
        proxyUseProperty.setValue(proxyPreferences.shouldUseProxy());
        proxyHostnameProperty.setValue(proxyPreferences.getHostname());
        proxyPortProperty.setValue(proxyPreferences.getPort());
        proxyUseAuthenticationProperty.setValue(proxyPreferences.shouldUseAuthentication());
        proxyUsernameProperty.setValue(proxyPreferences.getUsername());
        proxyPasswordProperty.setValue(proxyPreferences.getPassword());
        proxyPersistPasswordProperty.setValue(proxyPreferences.shouldPersistPassword());
        passwordPersistAvailable.setValue(OS.isKeyringAvailable());
    }

    private void setSSLValues() {
        customCertificateListProperty.clear();
        trustStoreManager.getCustomCertificates().forEach(cert -> customCertificateListProperty.add(CustomCertificateViewModel.fromSSLCertificate(cert)));
        customCertificateListProperty.addListener((ListChangeListener<CustomCertificateViewModel>) c -> {
            sslCertificatesChanged.set(true);
            while (c.next()) {
                if (c.wasAdded()) {
                    CustomCertificateViewModel certificate = c.getAddedSubList().getFirst();
                    certificate.getPath().ifPresent(path -> trustStoreManager
                            .addCertificate(formatCustomAlias(certificate.getThumbprint()), Path.of(path)));
                } else if (c.wasRemoved()) {
                    CustomCertificateViewModel certificate = c.getRemoved().getFirst();
                    trustStoreManager.deleteCertificate(formatCustomAlias(certificate.getThumbprint()));
                }
            }
        });
    }

    @Override
    public void storeSettings() {
        internalPreferences.setVersionCheckEnabled(versionCheckProperty.getValue());
        proxyPreferences.setUseProxy(proxyUseProperty.getValue());
        proxyPreferences.setHostname(proxyHostnameProperty.getValue().trim());
        proxyPreferences.setPort(proxyPortProperty.getValue().trim());
        proxyPreferences.setUseAuthentication(proxyUseAuthenticationProperty.getValue());
        proxyPreferences.setUsername(proxyUsernameProperty.getValue().trim());
        proxyPreferences.setPersistPassword(proxyPersistPasswordProperty.getValue()); // Set before the password to actually persist
        proxyPreferences.setPassword(proxyPasswordProperty.getValue());
        ProxyRegisterer.register(proxyPreferences);

        trustStoreManager.flush();
    }

    private Optional<Integer> getPortAsInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public ValidationStatus proxyHostnameValidationStatus() {
        return proxyHostnameValidator.getValidationStatus();
    }

    public ValidationStatus proxyPortValidationStatus() {
        return proxyPortValidator.getValidationStatus();
    }

    public ValidationStatus proxyUsernameValidationStatus() {
        return proxyUsernameValidator.getValidationStatus();
    }

    public ValidationStatus proxyPasswordValidationStatus() {
        return proxyPasswordValidator.getValidationStatus();
    }

    @Override
    public boolean validateSettings() {
        CompositeValidator validator = new CompositeValidator();

        if (proxyUseProperty.getValue()) {
            validator.addValidators(proxyHostnameValidator);
            validator.addValidators(proxyPortValidator);

            if (proxyUseAuthenticationProperty.getValue()) {
                validator.addValidators(proxyUsernameValidator);
                validator.addValidators(proxyPasswordValidator);
            }
        }

        ValidationStatus validationStatus = validator.getValidationStatus();
        if (!validationStatus.isValid()) {
            validationStatus.getHighestMessage().ifPresent(message ->
                    dialogService.showErrorDialogAndWait(message.getMessage()));
            return false;
        }
        return true;
    }

    /**
     * Check the connection by using the given url. Used for validating the http proxy. The checking result will be appear when request finished. The checking result could be either success or fail, if fail, the cause will be displayed.
     */
    public void checkConnection() {
        final String connectionSuccessText = Localization.lang("Connection successful!");
        final String connectionFailedText = Localization.lang("Connection failed!");
        final String dialogTitle = Localization.lang("Check Proxy Setting");

        final String testUrl = "http://jabref.org";

        ProxyRegisterer.register(new ProxyPreferences(
                proxyUseProperty.getValue(),
                proxyHostnameProperty.getValue().trim(),
                proxyPortProperty.getValue().trim(),
                proxyUseAuthenticationProperty.getValue(),
                proxyUsernameProperty.getValue().trim(),
                proxyPasswordProperty.getValue(),
                proxyPersistPasswordProperty.getValue()
        ));

        URLDownload urlDownload;
        try {
            urlDownload = new URLDownload(testUrl);
            if (urlDownload.canBeReached()) {
                dialogService.showInformationDialogAndWait(dialogTitle, connectionSuccessText);
            } else {
                dialogService.showErrorDialogAndWait(dialogTitle, connectionFailedText);
            }
        } catch (MalformedURLException e) {
            // Why would that happen? Because one of developers inserted a failing url in testUrl...
        } catch (UnirestException e) {
            dialogService.showErrorDialogAndWait(dialogTitle, connectionFailedText);
        }

        ProxyRegisterer.register(backupProxyPreferences);
    }

    @Override
    public List<String> getRestartWarnings() {
        if (sslCertificatesChanged.get()) {
            return List.of(Localization.lang("SSL configuration changed"));
        } else {
            return List.of();
        }
    }

    public BooleanProperty versionCheckProperty() {
        return versionCheckProperty;
    }

    public BooleanProperty proxyUseProperty() {
        return proxyUseProperty;
    }

    public StringProperty proxyHostnameProperty() {
        return proxyHostnameProperty;
    }

    public StringProperty proxyPortProperty() {
        return proxyPortProperty;
    }

    public BooleanProperty proxyUseAuthenticationProperty() {
        return proxyUseAuthenticationProperty;
    }

    public StringProperty proxyUsernameProperty() {
        return proxyUsernameProperty;
    }

    public StringProperty proxyPasswordProperty() {
        return proxyPasswordProperty;
    }

    public BooleanProperty proxyPersistPasswordProperty() {
        return proxyPersistPasswordProperty;
    }

    public ReadOnlyBooleanProperty passwordPersistAvailable() {
        return passwordPersistAvailable;
    }

    public ListProperty<CustomCertificateViewModel> customCertificateListProperty() {
        return customCertificateListProperty;
    }

    public void addCertificateFile() {
        FileDialogConfiguration fileDialogConfiguration = new FileDialogConfiguration.Builder()
                .addExtensionFilter(new FileChooser.ExtensionFilter(Localization.lang("SSL certificate file"), "*.crt", "*.cer"))
                .withDefaultExtension(Localization.lang("SSL certificate file"), StandardFileType.CER)
                .withInitialDirectory(preferences.getFilePreferences().getWorkingDirectory())
                .build();

        dialogService.showFileOpenDialog(fileDialogConfiguration).ifPresent(certPath -> SSLCertificate.fromPath(certPath).ifPresent(sslCertificate -> {
            if (!trustStoreManager.certificateExists(formatCustomAlias(sslCertificate.getSHA256Thumbprint()))) {
                customCertificateListProperty.add(CustomCertificateViewModel.fromSSLCertificate(sslCertificate)
                                                                            .setPath(certPath.toAbsolutePath().toString()));
            } else {
                dialogService.showWarningDialogAndWait(Localization.lang("Duplicate Certificates"), Localization.lang("You already added this certificate"));
            }
        }));
    }

    private String formatCustomAlias(String thumbprint) {
        return "%s[custom]".formatted(thumbprint);
    }
}
