package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import java.util.Date; // 
import java.text.SimpleDateFormat;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.toedter.calendar.JDateChooser;
import java.util.Date;

public class QRScanner {
    private static final String ADMIN_USERNAME = "0101";
    private static final String ADMIN_PASSWORD = "000010000";
    private static final String BASE_DIR = System.getProperty("user.dir");
    private static final String USER_DATABASE = BASE_DIR + "\\users.txt";
    private static final String MEDICATION_DATABASE = BASE_DIR + "\\medication.txt";
    private static final String STAFF_DATABASE = BASE_DIR + "\\staff.txt";
    private static final String DOCTORS_DATABASE = BASE_DIR + "\\doctors.txt";
    private static final String ASSESSMENT_DATABASE = BASE_DIR + "\\Assessment.txt";
    private static final String VITALS_DATABASE = BASE_DIR + "\\vibs.txt";
    private static final long SCAN_INTERVAL = 500; // milliseconds
    private static final long SCAN_COOLDOWN = 5000; // 5 seconds cooldown after successful scan

    private static List<User> users = new ArrayList<>();
    private static User currentUser = null;
    private static User currentPatient = null;
    private static Webcam webcam;
    private static JFrame scannerWindow;
    private static ScheduledExecutorService executor;
    private static boolean scanningActive = false;
    private static long lastScanTime = 0;
    private static String lastScannedContent = null;

    private static JFrame mainFrame;
    private static CardLayout cardLayout;
    private static JPanel cardPanel;
    private static JTextField loginUserField;
    private static JPasswordField loginPassField;

    private static final String[] COMMON_ALLERGIES = {
            // Food Allergies
            "Food: Milk", "Food: Eggs", "Food: Peanuts", "Food: Tree Nuts", "Food: Soy",
            "Food: Wheat", "Food: Fish", "Food: Shellfish", "Food: Sesame", "Food: Mustard",

            // Environmental Allergies
            "Environmental: Pollen", "Environmental: Dust Mites", "Environmental: Mold",
            "Environmental: Animal Dander",

            // Medication Allergies
            "Medication: Penicillin", "Medication: Sulfonamides", "Medication: Aspirin/NSAIDs",
            "Medication: Latex",

            // Insect Sting Allergies
            "Insect: Bee stings", "Insect: Wasp stings", "Insect: Ant stings",

            // Other
            "Other (specify)"
    };

    private static final String[] COMMON_MEDICAL_CONDITIONS = {
            // Cardiovascular
            "Cardiovascular: Hypertension", "Cardiovascular: Hyperlipidemia",
            "Cardiovascular: Coronary Artery Disease", "Cardiovascular: Heart Failure",
            "Cardiovascular: Arrhythmia",

            // Respiratory
            "Respiratory: Asthma", "Respiratory: COPD",
            "Respiratory: Allergic Rhinitis", "Respiratory: Sleep Apnea",

            // Endocrine/Metabolic
            "Endocrine: Diabetes Mellitus", "Endocrine: Hypothyroidism",
            "Endocrine: Hyperthyroidism",

            // Gastrointestinal
            "Gastrointestinal: GERD", "Gastrointestinal: IBS",

            // Neurological
            "Neurological: Migraine", "Neurological: Epilepsy",

            // Mental Health
            "Mental Health: Anxiety Disorder", "Mental Health: Depression",

            // Musculoskeletal
            "Musculoskeletal: Osteoarthritis", "Musculoskeletal: Rheumatoid Arthritis",

            // Other
            "Other (specify)"
    };

    private static final String[] MEDICAL_PROFESSIONS = {
            "Nurse", "Pharmacist",
            "Physiotherapist", "Dentist", "Paramedic", "Other"
    };

    public static void main(String[] args) {
        // Create data directory if it doesn't exist
        File dataDir = new File(BASE_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    
        Path verificationDir = Paths.get(BASE_DIR + File.separator + "verification_images");
        try {
            Files.createDirectories(verificationDir);
        } catch (IOException e) {
            System.err.println("Could not create verification images directory: " + e.getMessage());
        }
        loadUsersFromFile();

        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static void loadUsersFromFile() {
        users.clear(); // Clear existing users

        try {
            Path path = Paths.get(USER_DATABASE);

            // Create file if it doesn't exist
            if (!Files.exists(path)) {
                Files.createFile(path);
                return;
            }

            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    User user = User.fromString(line);
                    if (user != null) {
                        users.add(user);
                    }
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error loading user data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void saveUserToFile(User user) {
        try {
            // First load all existing users
            loadUsersFromFile();

            // Check if this user already exists
            boolean userExists = false;
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).username.equals(user.username)) {
                    users.set(i, user); // actually updates the list
                    userExists = true;
                    break;
                }
            }

            // If new user, add to list
            if (!userExists) {
                users.add(user);
            }

            // Now overwrite the entire file
            FileWriter fw = new FileWriter(USER_DATABASE, false); // false to overwrite
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);

            for (User u : users) {
                out.println(u.toStringForFile());
            }
            out.close();

            loadUsersFromFile(); // Add this line
            SwingUtilities.invokeLater(() -> {
                if (currentUser != null) {
                    currentUser = users.stream()
                            .filter(u -> u.username.equals(currentUser.username))
                            .findFirst()
                            .orElse(null);
                }
                // Force UI refresh
                mainFrame.revalidate();
                mainFrame.repaint();
            });

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error saving user data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void createAndShowGUI() {
        mainFrame = new JFrame("E-Health Card System");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(500, 400);
        mainFrame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Create panels for different screens
        createMainMenuPanel();
        createLoginPanel();
        createAddUserAccountPanel();
        createAdminMenuPanel();
        createUserMenuPanel();
        createStaffMenuPanel(); // Add this line
        createDoctorMenuPanel(); // Add this line
        mainFrame.add(cardPanel);
        mainFrame.setVisible(true);
    }

    private static void setupAutoAgeCalculation(JTextField birthdayField, JTextField ageField) {
        birthdayField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateAge();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateAge();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateAge();
            }

            private void updateAge() {
                SwingUtilities.invokeLater(() -> {
                    String bday = birthdayField.getText().trim();
                    if (bday.matches("^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/(19|20)\\d\\d$")) {
                        try {
                            String[] parts = bday.split("/");
                            LocalDate birthDate = LocalDate.of(
                                    Integer.parseInt(parts[2]),
                                    Integer.parseInt(parts[0]),
                                    Integer.parseInt(parts[1]));
                            int age = Period.between(birthDate, LocalDate.now()).getYears();
                            ageField.setText(String.valueOf(age));
                        } catch (Exception ex) {
                            ageField.setText("");
                        }
                    } else {
                        ageField.setText("");
                    }
                });
            }
        });
    }

    // Add the addField() method HERE
    private static void addField(JPanel panel, GridBagConstraints gbc,
            String label, JComponent field, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private static void createMainMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("E-Health Card System", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(titleLabel, gbc);

        JButton loginButton = new JButton("LOG IN");
        loginButton.addActionListener(e -> cardLayout.show(cardPanel, "Login"));
        panel.add(loginButton, gbc);

        JButton exitButton = new JButton("EXIT");
        exitButton.addActionListener(e -> System.exit(0));
        panel.add(exitButton, gbc);

        cardPanel.add(panel, "MainMenu");
    }

    private static void createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Login", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(titleLabel, gbc);

        JLabel userLabel = new JLabel("Username:");
        panel.add(userLabel, gbc);

        loginUserField = new JTextField(15);
        panel.add(loginUserField, gbc);

        JLabel passLabel = new JLabel("Password:");
        panel.add(passLabel, gbc);

        loginPassField = new JPasswordField(15);
        panel.add(loginPassField, gbc);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(e -> {
            String username = loginUserField.getText().trim();
            String password = new String(loginPassField.getPassword()).trim();

            if (username.equals(ADMIN_USERNAME) && password.equals(ADMIN_PASSWORD)) {
                cardLayout.show(cardPanel, "AdminMenu");
            } else {
                for (User user : users) {
                    if (user.username.equals(username) && user.password.equals(password)) {
                        currentUser = user;
                        cardLayout.show(cardPanel, "UserMenu");
                        return;
                    }
                }
                List<User> staffList = loadStaffFromFile();
                for (User staff : staffList) {
                    if (staff.username.equals(username) && staff.password.equals(password)) {
                        currentUser = staff;
                        cardLayout.show(cardPanel, "StaffMenu");
                        return;
                    }
                }

                List<User> doctors = loadDoctorsFromFile();
                for (User doctor : doctors) {
                    if (doctor.username.equals(username) && doctor.password.equals(password)) {
                        currentUser = doctor;
                        cardLayout.show(cardPanel, "DoctorMenu");
                        return;
                    }
                }

                JOptionPane.showMessageDialog(panel, "Invalid credentials", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(loginButton, gbc);

        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> {
            loginUserField.setText("");
            loginPassField.setText("");
            cardLayout.show(cardPanel, "MainMenu");
        });
        panel.add(backButton, gbc);

        cardPanel.add(panel, "Login");
    }

    private static void createAddUserAccountPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title
        JLabel titleLabel = new JLabel("Add User Account (Staff)", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Main content panel with two columns
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 20, 10));

        // ================== LEFT PANEL ==================
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Name Components
        JTextField nameField = new JTextField(15);
        JLabel nameError = new JLabel();
        addFormField(leftPanel, gbc, "Name (First, Last, MI):", nameField, nameError, row++);

        // Birthday Components
        JTextField birthdayField = new JTextField(15);
        JLabel birthdayError = new JLabel();
        addFormField(leftPanel, gbc, "Birthday (MM/DD/YYYY):", birthdayField, birthdayError, row++);

        // Age Components (Auto-calculated)
        JTextField ageField = new JTextField(15);
        ageField.setEditable(false);
        JLabel ageError = new JLabel();
        addFormField(leftPanel, gbc, "Age:", ageField, ageError, row++);

        // Setup auto age calculation
        setupAutoAgeCalculation(birthdayField, ageField);

        // Civil Status Components
        JTextField civilStatusField = new JTextField(15);
        JLabel civilStatusError = new JLabel();
        addFormField(leftPanel, gbc, "Civil Status:", civilStatusField, civilStatusError, row++);

        // Blood Type Components
        JTextField bloodTypeField = new JTextField(15);
        JLabel bloodTypeError = new JLabel();
        addFormField(leftPanel, gbc, "Blood Type:", bloodTypeField, bloodTypeError, row++);

        // Contact Number Components
        JTextField contactNumberField = new JTextField(15);
        JLabel contactNumberError = new JLabel();
        addFormField(leftPanel, gbc, "Contact Number (XXXX-XXX-XXXX):", contactNumberField, contactNumberError, row++);

        // Emergency Contact Components
        JTextField emergencyContactField = new JTextField(15);
        JLabel emergencyContactError = new JLabel();
        addFormField(leftPanel, gbc, "Emergency Contact (XXXX-XXX-XXXX):", emergencyContactField, emergencyContactError,
                row++);

        // ================== RIGHT PANEL ==================
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.insets = new Insets(5, 5, 5, 5);
        rightGbc.anchor = GridBagConstraints.WEST;
        rightGbc.fill = GridBagConstraints.HORIZONTAL;

        int rightRow = 0;

        // Username
        JTextField usernameField = new JTextField(15);
        addFormField(rightPanel, rightGbc, "Username:", usernameField, new JLabel(), rightRow++);

        // Password
        JPasswordField passwordField = new JPasswordField(15);
        addFormField(rightPanel, rightGbc, "Password (12 digits):", passwordField, new JLabel(), rightRow++);

        // Image Components
        JLabel imagePreview = new JLabel();
        imagePreview.setPreferredSize(new Dimension(150, 150));
        imagePreview.setHorizontalAlignment(JLabel.CENTER);
        imagePreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        final String[] imagePath = new String[1];

        JButton addImageButton = new JButton("Add Picture");
        addImageButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Profile Picture");
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.addChoosableFileFilter(
                    new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes()));

            if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                try {
                    File selectedFile = fileChooser.getSelectedFile();
                    BufferedImage originalImage = ImageIO.read(selectedFile);
                    Image scaledImage = originalImage.getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                    imagePreview.setIcon(new ImageIcon(scaledImage));
                    imagePath[0] = selectedFile.getAbsolutePath();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(panel,
                            "Error loading image: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        rightGbc.gridx = 0;
        rightGbc.gridy = rightRow++;
        rightGbc.gridwidth = 2;
        rightPanel.add(imagePreview, rightGbc);

        rightGbc.gridy = rightRow++;
        rightPanel.add(addImageButton, rightGbc);

        contentPanel.add(leftPanel);
        contentPanel.add(rightPanel);

        // ================== BUTTON PANEL ==================
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton createButton = new JButton("Create User");
        JButton backButton = new JButton("Back");

        createButton.addActionListener(e -> {
            // Reset errors
            nameError.setText("");
            birthdayError.setText("");
            ageError.setText("");
            civilStatusError.setText("");
            bloodTypeError.setText("");
            contactNumberError.setText("");
            emergencyContactError.setText("");

            boolean isValid = true;

            // Name validation
            String name = nameField.getText().trim();
            if (!name.matches("[A-Za-z]+(\\s[A-Za-z]+)+")) {
                nameError.setText("Format: First Last MI");
                isValid = false;
            }

            // Birthday validation
            String birthday = birthdayField.getText().trim();
            if (!birthday.matches("^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/(19|20)\\d\\d$")) {
                birthdayError.setText("Format: MM/DD/YYYY");
                isValid = false;
            }

            // Auto-validated age
            if (ageField.getText().isEmpty()) {
                birthdayError.setText("Invalid date");
                isValid = false;
            }

            // Civil status validation
            String civilStatus = civilStatusField.getText().trim();
            if (!civilStatus.equalsIgnoreCase("Single") && !civilStatus.equalsIgnoreCase("Married")) {
                civilStatusError.setText("Must be Single/Married");
                isValid = false;
            }

            // Blood type validation
            String bloodType = bloodTypeField.getText().trim().toUpperCase();
            if (!bloodType.matches("^(A|B|AB|O)[+-]$")) {
                bloodTypeError.setText("Invalid blood type");
                isValid = false;
            }

            // Contact number validation
            String contactNumber = contactNumberField.getText().trim();
            if (!contactNumber.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                contactNumberError.setText("Invalid format");
                isValid = false;
            }

            // Emergency contact validation
            String emergencyContact = emergencyContactField.getText().trim();
            if (!emergencyContact.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                emergencyContactError.setText("Invalid format");
                isValid = false;
            }

            // Password validation
            if (new String(passwordField.getPassword()).length() != 12) {
                JOptionPane.showMessageDialog(panel, "Password must be 12 digits", "Error", JOptionPane.ERROR_MESSAGE);
                isValid = false;
            }

            // Username uniqueness check
            String username = usernameField.getText().trim();
            for (User u : users) {
                if (u.username.equals(username)) {
                    JOptionPane.showMessageDialog(panel, "Username already exists", "Error", JOptionPane.ERROR_MESSAGE);
                    isValid = false;
                    break;
                }
            }

            if (isValid) {
                User user = new User();
                user.id = users.size() + 1;
                user.dateAdded = LocalDate.now().toString();
                user.name = name;
                user.age = Integer.parseInt(ageField.getText()); // Use auto-calculated age
                user.birthday = birthday;
                user.civilStatus = civilStatus;
                user.bloodType = bloodType;
                user.contactNumber = contactNumber;
                user.emergencyContactNumber = emergencyContact;
                user.username = username;
                user.password = new String(passwordField.getPassword());
                user.imagePath = imagePath[0];

                saveUserToFile(user);
                JOptionPane.showMessageDialog(panel, "User account created successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);

                // Clear all fields
                nameField.setText("");
                birthdayField.setText("");
                ageField.setText("");
                civilStatusField.setText("");
                bloodTypeField.setText("");
                contactNumberField.setText("");
                emergencyContactField.setText("");
                usernameField.setText("");
                passwordField.setText("");
                imagePreview.setIcon(null);
                imagePath[0] = null;

                cardLayout.show(cardPanel, "StaffMenu");
            }
        });

        backButton.addActionListener(e -> {
            // Clear fields
            nameField.setText("");
            birthdayField.setText("");
            ageField.setText("");
            civilStatusField.setText("");
            bloodTypeField.setText("");
            contactNumberField.setText("");
            emergencyContactField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            imagePreview.setIcon(null);
            imagePath[0] = null;

            cardLayout.show(cardPanel, "StaffMenu");
        });

        buttonPanel.add(createButton);
        buttonPanel.add(backButton);

        panel.add(contentPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        cardPanel.add(panel, "AddUserAccount");
    }

    // Overloaded method without error label
    private static void addFormField(JPanel panel, GridBagConstraints gbc, String label, Component field, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
        gbc.fill = GridBagConstraints.NONE;
    }

    // Overloaded method with error label
    private static void addFormField(JPanel panel, GridBagConstraints gbc, String label, Component field,
            JLabel errorLabel, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
        gbc.gridx = 2;
        panel.add(errorLabel, gbc);
    }

    private static boolean validateMedicationFields(JTextField... fields) {
        for (JTextField field : fields) {
            if (field.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "All fields are required!", "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        // Add date validation if needed
        return true;
    }

    private static void createAdminMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Welcome Admin", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(titleLabel, gbc);

        // --- MODIFICATION START ---
        String[] options = {
                "1. Add",
                "2. Remove",
                "3. Edit User Profile",
                "4. View Profile",
                "5. Masterlist",
                "6. Generate QR Code",
                "7. Scan QR Code",
                "8. Manage Staff",
                "9. Generate PDF Report",
                "10. Exit"
        };

        for (String option : options) {
            JButton button = new JButton(option);
            button.addActionListener(e -> {
                String choice = option.substring(0, option.indexOf("."));
                switch (choice) {
                    case "1" -> showAdminAddMenuDialog(); // New method for the "Add" feature
                    case "2" -> showRemoveMenuDialog();
                    case "3" -> showEditMenuDialog();
                    case "4" -> showProfilesDialog();
                    case "5" -> showMasterlistDialog(true); // Admin sees all
                    case "6" -> showGenerateQRCodeDialog();
                    case "7" -> startScanner();
                    case "8" -> showManageStaffDialog();
                    case "9" -> showGeneratePDFDialog();
                    case "10" -> {
                        loginUserField.setText("");
                        loginPassField.setText("");
                        cardLayout.show(cardPanel, "MainMenu");
                    }
                }
            });
            panel.add(button, gbc);
        }
        // --- MODIFICATION END ---

        cardPanel.add(panel, "AdminMenu");
    }

    private static void showGeneratePDFDialog() {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user to generate PDF for:",
                "Generate PDF Report",
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user != null) {
            generateUserPDF(user);
        }
    }

    private static void generateUserPDF(User user) {
        // Load current and past medications
        List<String[]> currentMeds = loadMedicationsForUser(user.username, false);
        List<String[]> pastMeds = loadMedicationsForUser(user.username, true);

        // Generate PDF in a separate thread to avoid UI freezing
        new Thread(() -> {
            PDFGenerator.generateUserPDF(user, currentMeds, pastMeds);
        }).start();
    }

    private static void createUserMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("User Menu", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(titleLabel, gbc);

        // Combined Profile button (replaces separate buttons)
        JButton profileButton = new JButton("1. Profile");
        profileButton.addActionListener(e -> showUserProfileTabs());
        panel.add(profileButton, gbc);

        JButton qrButton = new JButton("2. Generate QR Code");
        qrButton.addActionListener(e -> generateUserQRCode());
        panel.add(qrButton, gbc);

        JButton manageButton = new JButton("3. Manage Account");
        manageButton.addActionListener(e -> showManageAccountDialog());
        panel.add(manageButton, gbc);

        JButton pdfButton = new JButton("4. Generate PDF Report");
        pdfButton.addActionListener(e -> generateUserPDF(currentUser));
        panel.add(pdfButton, gbc);

        // Update the exit button number
        JButton exitButton = new JButton("5. Exit");
        exitButton.addActionListener(e -> {
            currentUser = null;
            loginUserField.setText("");
            loginPassField.setText("");
            cardLayout.show(cardPanel, "MainMenu");
        });
        panel.add(exitButton, gbc);

        cardPanel.add(panel, "UserMenu");
    }

    private static void showUserProfileTabs() {
        if (currentUser == null)
            return;

        JDialog profileDialog = new JDialog(mainFrame, "My Profile - " + currentUser.name, true);
        profileDialog.setSize(900, 600);
        profileDialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Basic Profile
        tabbedPane.addTab("Profile", createProfileTab(currentUser));

        // Tab 2: Medical History
        JPanel historyPanel = new JPanel(new BorderLayout());
        JTextArea historyText = new JTextArea();
        historyText.setEditable(false);

        if (currentUser.medicalHistory.isEmpty()) {
            historyText.setText("No medical history available.");
        } else {
            for (String[] history : currentUser.medicalHistory) {
                historyText.append("• " + history[0] + "\n");
                historyText.append("  Date: " + history[1] + "\n\n");
            }
        }
        historyPanel.add(new JScrollPane(historyText), BorderLayout.CENTER);
        tabbedPane.addTab("Medical History", historyPanel);

        // Tab 3: Current Medications
        JTextArea currentMedsArea = new JTextArea();
        currentMedsArea.setEditable(false);
        List<String[]> currentMeds = loadMedicationsForUser(currentUser.username, false);
        if (currentMeds.isEmpty()) {
            currentMedsArea.setText("No current medications.");
        } else {
            for (String[] med : currentMeds) {
                currentMedsArea.append("• " + med[1] + " - " + med[2] + "\n");
                currentMedsArea.append("  Instructions: " + med[3] + "\n");
                currentMedsArea.append("  From: " + med[4] + " to " + med[5] + "\n\n");
            }
        }
        tabbedPane.addTab("Current Medications", new JScrollPane(currentMedsArea));

        // Tab 4: Medication History
        JTextArea pastMedsArea = new JTextArea();
        pastMedsArea.setEditable(false);
        List<String[]> pastMeds = loadMedicationsForUser(currentUser.username, true);
        if (pastMeds.isEmpty()) {
            pastMedsArea.setText("No past medications.");
        } else {
            for (String[] med : pastMeds) {
                pastMedsArea.append("• " + med[1] + " - " + med[2] + "\n");
                pastMedsArea.append("  Instructions: " + med[3] + "\n");
                pastMedsArea.append("  From: " + med[4] + " to " + med[5] + "\n\n");
            }
        }
        tabbedPane.addTab("Medication History", new JScrollPane(pastMedsArea));

        profileDialog.add(tabbedPane);
        profileDialog.setVisible(true);
    }

    private static void showGenerateQRCodeDialog() {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user to generate QR code for:",
                "Generate QR Code",
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user == null)
            return;

        generateQRCodeForUser(user);
    }

    private static void generateUserQRCode() {
        if (currentUser == null)
            return;
        generateQRCodeForUser(currentUser);
    }

    private static void generateQRCodeForUser(User user) {
        // Create QR code content
        String qrContent = String.format(
                "E-Health Card\nUsername: %s\nName: %s\nAge: %d\nBlood Type: %s\nContact: %s\nEmergency: %s\nAllergies: %s\nConditions: %s",
                user.username,
                user.name,
                user.age,
                user.bloodType,
                user.contactNumber,
                user.emergencyContactNumber,
                String.join(", ", user.allergies),
                String.join(", ", user.medicalConditions));

        try {
            // Generate QR code image in memory
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 350, 350);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Create dialog to display QR code
            JDialog qrDialog = new JDialog(mainFrame, "QR Code for " + user.name, true);
            qrDialog.setLayout(new BorderLayout());
            qrDialog.setSize(400, 450);
            qrDialog.setLocationRelativeTo(mainFrame);

            // Add QR code image
            JLabel qrLabel = new JLabel(new ImageIcon(qrImage));
            qrLabel.setHorizontalAlignment(JLabel.CENTER);
            qrDialog.add(qrLabel, BorderLayout.CENTER);

            // Add download button
            JButton downloadButton = new JButton("Download QR Code");
            downloadButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Save QR Code As");
                fileChooser.setSelectedFile(new File(user.username + "_qr.png"));

                int userSelection = fileChooser.showSaveDialog(qrDialog);
                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = fileChooser.getSelectedFile();
                    String filePath = fileToSave.getAbsolutePath();

                    // Ensure .png extension
                    if (!filePath.toLowerCase().endsWith(".png")) {
                        filePath += ".png";
                    }

                    try {
                        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", Paths.get(filePath));
                        JOptionPane.showMessageDialog(qrDialog,
                                "QR Code saved successfully!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(qrDialog,
                                "Error saving QR code: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            JPanel buttonPanel = new JPanel();
            buttonPanel.add(downloadButton);
            qrDialog.add(buttonPanel, BorderLayout.SOUTH);

            qrDialog.setVisible(true);
        } catch (WriterException e) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Error generating QR code: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void startScanner() {
        scanningActive = true;
        lastScanTime = 0;
        lastScannedContent = null;

        // Initialize webcam
        webcam = Webcam.getDefault();
        if (webcam == null) {
            JOptionPane.showMessageDialog(
                    null,
                    "No webcam detected!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            scanningActive = false;
            return;
        }

        webcam.setViewSize(WebcamResolution.VGA.getSize());
        webcam.open();

        // Create scanner window
        scannerWindow = new JFrame("QR Code Scanner");
        scannerWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        scannerWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanupScanner();
            }
        });
        scannerWindow.setLayout(new BorderLayout());

        // Add webcam preview
        WebcamPanel webcamPanel = new WebcamPanel(webcam);
        webcamPanel.setMirrored(true);
        scannerWindow.add(webcamPanel, BorderLayout.CENTER);

        // Add status label and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JLabel statusLabel = new JLabel("Scanning for QR codes...", JLabel.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton stopButton = new JButton("Stop Scanner");

        stopButton.addActionListener(e -> {
            cleanupScanner();
            scannerWindow.dispose();
        });

        buttonPanel.add(stopButton);

        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        scannerWindow.add(bottomPanel, BorderLayout.SOUTH);

        scannerWindow.pack();
        scannerWindow.setLocationRelativeTo(null);
        scannerWindow.setVisible(true);

        // Create scanner service
        executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            if (!webcam.isOpen() || !scanningActive)
                return;

            // Check if we're in cooldown period
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime < SCAN_COOLDOWN) {
                return;
            }

            BufferedImage image = webcam.getImage();
            if (image != null) {
                try {
                    LuminanceSource source = new BufferedImageLuminanceSource(image);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                    Result result = new MultiFormatReader().decode(bitmap);
                    if (result != null) {
                        String currentContent = result.getText();

                        // Check if this is a new QR code or the same one
                        if (currentContent.equals(lastScannedContent)) {
                            return; // Ignore duplicate
                        }

                        lastScannedContent = currentContent;
                        lastScanTime = currentTime;

                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("QR Code detected!");
                            showPatientProfileFromQR(currentContent);
                            statusLabel.setText("Ready to scan next code...");
                        });
                    }
                } catch (NotFoundException e) {
                    // QR code not found - continue scanning
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Error: " + e.getMessage()));
                }
            }
        }, 0, SCAN_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private static void showPatientProfileFromQR(String qrContent) {
        // Parse the QR content to find the username
        String username = extractUsernameFromQR(qrContent);

        // Find the user in our database based on the username from the QR code
        User patient = users.stream()
                .filter(u -> u.username.equals(username))
                .findFirst()
                .orElse(null);

        if (patient != null) {
            // Create a dialog with all patient information tabs
            JDialog patientDialog = new JDialog(mainFrame, "Patient Profile - " + patient.name, true);
            patientDialog.setSize(900, 600);
            patientDialog.setLocationRelativeTo(mainFrame);

            JTabbedPane tabbedPane = new JTabbedPane();

            // Tab 1: Profile
            tabbedPane.addTab("Profile", createProfileTab(patient));

            // Tab 2: Medical History
            JPanel historyPanel = new JPanel(new BorderLayout());
            JTextArea historyText = new JTextArea(patient.getMedicalHistoryAsString());
            historyText.setEditable(false);
            historyPanel.add(new JScrollPane(historyText), BorderLayout.CENTER);
            tabbedPane.addTab("Medical History", historyPanel);

            // Tab 3: Current Medications
            JTextArea currentMedsArea = new JTextArea();
            currentMedsArea.setEditable(false);
            List<String[]> currentMeds = loadMedicationsForUser(patient.username, false);
            if (currentMeds.isEmpty()) {
                currentMedsArea.setText("No current medications.");
            } else {
                for (String[] med : currentMeds) {
                    currentMedsArea.append("Medication: " + med[1] + "\n");
                    currentMedsArea.append("Dosage: " + med[2] + "\n");
                    currentMedsArea.append("Instructions: " + med[3] + "\n");
                    currentMedsArea.append("Period: " + med[4] + " to " + med[5] + "\n");
                    currentMedsArea.append("--------\n");
                }
            }
            tabbedPane.addTab("Current Medications", new JScrollPane(currentMedsArea));

            // Tab 4: Medication History
            JTextArea pastMedsArea = new JTextArea();
            pastMedsArea.setEditable(false);
            List<String[]> pastMeds = loadMedicationsForUser(patient.username, true);
            if (pastMeds.isEmpty()) {
                pastMedsArea.setText("No past medications.");
            } else {
                for (String[] med : pastMeds) {
                    pastMedsArea.append("Medication: " + med[1] + "\n");
                    pastMedsArea.append("Dosage: " + med[2] + "\n");
                    pastMedsArea.append("Instructions: " + med[3] + "\n");
                    pastMedsArea.append("Period: " + med[4] + " to " + med[5] + "\n");
                    pastMedsArea.append("--------\n");
                }
            }
            tabbedPane.addTab("Medication History", new JScrollPane(pastMedsArea));

            // Tab 5: Vitals Assessment
            tabbedPane.addTab("Vitals Assessment", createViewVitalsPanel(patient));

            patientDialog.add(tabbedPane);
            patientDialog.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(scannerWindow,
                    "Patient not found in database.",
                    "Not Found",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Creates and shows a tabbed dialog for managing a specific patient's details.
     * This is now used after a QR scan or after selecting a referred patient.
     * 
     * @param patient The patient whose details are to be displayed.
     */
    private static void showPatientDetailsTabs(User patient) {
        if (patient == null) {
            JOptionPane.showMessageDialog(mainFrame, "Patient data could not be found or loaded.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create the main dialog window for the patient
        JDialog managementDialog = new JDialog(mainFrame, "Patient Management: " + patient.name, true);
        managementDialog.setSize(800, 600);
        managementDialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: View Vital Assessment
        tabs.addTab("Vital Assessment", createViewVitalsPanel(patient));

        // Tab 2: Add Medication
        tabs.addTab("Add Medication", createDoctorMedicationPanel(patient));

        // Tab 3: Add Medical History
        tabs.addTab("Add Medical History", createDoctorHistoryPanel(patient));

        managementDialog.add(tabs);
        managementDialog.setVisible(true);
    }

    private static String extractUsernameFromQR(String qrContent) {
        // The QR code now includes "Username: [username]" in the content
        for (String line : qrContent.split("\n")) {
            if (line.startsWith("Username: ")) {
                return line.substring("Username: ".length()).trim();
            }
        }
        return null;
    }

    private static void cleanupScanner() {
        scanningActive = false;
        lastScannedContent = null;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        if (webcam != null) {
            webcam.close();
            webcam = null;
        }
    }

    public static void generateQRCodeImage(String text, int width, int height, String filePath)
            throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        Path path = FileSystems.getDefault().getPath(filePath);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
    }

    private static void updateDisplayArea(JTextArea area, User user) {
        area.setText("Allergies: " + String.join(", ", user.allergies) + "\n\n" +
                "Medical Conditions: " + String.join(", ", user.medicalConditions));
    }

    private static JPanel createDoctorMedicationPanel(User patient) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        JTextField medNameField = new JTextField(25);
        JTextField dosageField = new JTextField(25);
        JTextArea instructionsArea = new JTextArea(4, 25);
        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);

        // --- MODIFICATION START ---
        // Create a JDateChooser for date selection and a JTextField for time
        JDateChooser endDateChooser = new JDateChooser();
        endDateChooser.setDateFormatString("MM/dd/yyyy");
        endDateChooser.setDate(new Date()); // Default to today's date

        JTextField endTimeField = new JTextField("HH:mm", 8);

        // Create a panel to hold both the date chooser and the time field
        JPanel dateTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        dateTimePanel.add(endDateChooser);
        dateTimePanel.add(endTimeField);
        // --- MODIFICATION END ---

        // Add form fields
        addField(formPanel, gbc, "Medication Name:", medNameField, row++);
        addField(formPanel, gbc, "Dosage:", dosageField, row++);
        addField(formPanel, gbc, "Special Instructions:", new JScrollPane(instructionsArea), row++);

        // --- MODIFICATION START ---
        // Manually add the label and the new dateTimePanel to the form
        gbc.gridx = 0;
        gbc.gridy = row;
        formPanel.add(new JLabel("Ending Date & Time:"), gbc);
        gbc.gridx = 1;
        formPanel.add(dateTimePanel, gbc);
        row++;
        // --- MODIFICATION END ---

        JButton saveButton = new JButton("Save Medication");
        saveButton.addActionListener(e -> {
            Date selectedDate = endDateChooser.getDate();
            String timeText = endTimeField.getText().trim();
            String endDateString = "";

            if (medNameField.getText().trim().isEmpty() || dosageField.getText().trim().isEmpty()) {
                // ... error handling ...
                return;
            }
            if (selectedDate == null) {
                // ... error handling ...
                return;
            }
            if (!timeText.matches("^([01][0-9]|2[0-3]):[0-5][0-9]$")) {
                // ... error handling ...
                return;
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            endDateString = dateFormat.format(selectedDate) + " " + timeText;

            // --- MODIFICATION START: Get doctor's name and pass it ---
            String doctorName = (currentUser != null) ? currentUser.name : "Unknown Doctor"; // Get current doctor's
                                                                                             // name

            saveMedicationToFile(patient.username,
                    medNameField.getText(),
                    dosageField.getText(),
                    instructionsArea.getText(),
                    new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date()), // Current date-time as start
                    endDateString,
                    doctorName); // Pass the doctor's name
            // --- MODIFICATION END ---

            JOptionPane.showMessageDialog(panel, "Medication added successfully!", "Success",
                    JOptionPane.INFORMATION_MESSAGE);

            // Clear fields
            medNameField.setText("");
            dosageField.setText("");
            instructionsArea.setText("");
            endDateChooser.setDate(new Date());
            endTimeField.setText("HH:mm");
        });
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        formPanel.add(saveButton, gbc);

        panel.add(formPanel, BorderLayout.CENTER);
        return panel;
    }

    private static void showAddMedicationDialog(User patient) {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user:",
                "Add Medication",
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user == null)
            return;

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        JTextField medNameField = new JTextField();
        JTextField dosageField = new JTextField();
        JTextArea specialInstructionsField = new JTextArea(3, 20);
        specialInstructionsField.setLineWrap(true);
        specialInstructionsField.setWrapStyleWord(true);

        JTextField startDateField = new JTextField("MM/DD/YYYY HH:mm");
        JTextField endDateField = new JTextField("MM/DD/YYYY HH:mm");

        panel.add(new JLabel("Medication Name:"));
        panel.add(medNameField);
        panel.add(new JLabel("Dosage Amount:"));
        panel.add(dosageField);
        panel.add(new JLabel("Special Instructions:"));
        panel.add(new JScrollPane(specialInstructionsField));
        panel.add(new JLabel("Starting Date & Military Time (MM/DD/YYYY HH:mm(Hours & Minutes)):"));
        panel.add(startDateField);
        panel.add(new JLabel("Ending Date & Military Time (MM/DD/YYYY HH:mm(Hours & Minutes)):"));
        panel.add(endDateField);

        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                panel,
                "Add Medication",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String medName = medNameField.getText().trim();
            String dosage = dosageField.getText().trim();
            String instructions = specialInstructionsField.getText().trim();
            String start = startDateField.getText().trim();
            String end = endDateField.getText().trim();

            if (medName.isEmpty() || dosage.isEmpty() || start.isEmpty() || end.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "All fields except instructions are required.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Validate dates first (add date parsing if needed)
            String doctorName = (currentUser != null) ? currentUser.name : "Unknown Doctor";
            saveMedicationToFile(user.username, medName, dosage, instructions, start, end, doctorName);
        }
    }

    private static JPanel createDoctorHistoryPanel(User patient) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Main content panel with history input and image preview
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 10, 10));

        // Left panel - history input
        JPanel historyPanel = new JPanel(new BorderLayout());
        JTextArea historyArea = new JTextArea(10, 30);
        historyPanel.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        // Right panel - image preview
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBorder(BorderFactory.createTitledBorder("Verification Image"));
        JLabel imagePreview = new JLabel();
        imagePreview.setPreferredSize(new Dimension(200, 200));
        imagePreview.setHorizontalAlignment(JLabel.CENTER);
        imagePreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        final String[] imagePath = new String[1];

        JButton addImageButton = new JButton("Add Verification Image");
        addImageButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Verification Image");
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.addChoosableFileFilter(
                    new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes()));

            if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                try {
                    File selectedFile = fileChooser.getSelectedFile();
                    BufferedImage originalImage = ImageIO.read(selectedFile);
                    Image scaledImage = originalImage.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                    imagePreview.setIcon(new ImageIcon(scaledImage));
                    imagePath[0] = selectedFile.getAbsolutePath();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(panel,
                            "Error loading image: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        imagePanel.add(imagePreview, BorderLayout.CENTER);
        imagePanel.add(addImageButton, BorderLayout.SOUTH);

        contentPanel.add(historyPanel);
        contentPanel.add(imagePanel);

        // Save button
        JButton saveHistoryButton = new JButton("Add Medical History");
        saveHistoryButton.addActionListener(e -> {
            String historyText = historyArea.getText().trim();
            if (historyText.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Medical history cannot be empty.", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String verificationImagePath = null;
            if (imagePath[0] != null) {
                // ... (your existing image saving logic) ...
                try {
                    String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                    String extension = imagePath[0].substring(imagePath[0].lastIndexOf("."));
                    String newFilename = "verification_" + patient.username + "_" + timestamp + extension;
                    Path destination = Paths.get(BASE_DIR + File.separator + "verification_images", newFilename);
                    Files.createDirectories(destination.getParent());
                    Files.copy(Paths.get(imagePath[0]), destination, StandardCopyOption.REPLACE_EXISTING);
                    verificationImagePath = destination.toString();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(panel, "Error saving verification image: " + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            String currentDate = new SimpleDateFormat("MM/dd/yyyy").format(new Date());

            // --- MODIFICATION START: Add Doctor's Name ---
            String doctorName = (currentUser != null) ? currentUser.name : "Unknown Doctor"; // Get current doctor's
                                                                                             // name
            patient.medicalHistory.add(new String[] {
                    historyText,
                    currentDate,
                    verificationImagePath != null ? verificationImagePath : "",
                    doctorName // Add the doctor's name as the 4th element
            });
            // --- MODIFICATION END ---

            updateDatabaseFile(); // This should save the updated patient object with the new history format
            JOptionPane.showMessageDialog(panel, "Medical history added successfully!", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            historyArea.setText("");
            imagePreview.setIcon(null);
            imagePath[0] = null;
        });
        panel.add(contentPanel, BorderLayout.CENTER);
        panel.add(saveHistoryButton, BorderLayout.SOUTH);

        return panel;
    }

    private static void showAddMedicalHistoryDialog() {
        if (currentPatient == null) {
            JOptionPane.showMessageDialog(mainFrame, "No patient selected!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 1));
        JTextField historyField = new JTextField(15);
        JTextField dateField = new JTextField(15);

        panel.add(new JLabel("Medical History:"));
        panel.add(historyField);
        panel.add(new JLabel("When did it occur/show:"));
        panel.add(dateField);

        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                panel,
                "Add Medical History",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            if (!historyField.getText().isEmpty() && !dateField.getText().isEmpty()) {
                currentPatient.medicalHistory.add(new String[] {
                        historyField.getText(),
                        dateField.getText()
                });
                updateDatabaseFile();
                JOptionPane.showMessageDialog(mainFrame, "Medical history added successfully!");
            }
        }
    }

    private static JPanel createDoctorViewVitalsPanel(String referralData) {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea vitalsArea = new JTextArea();
        vitalsArea.setEditable(false);
        vitalsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        String[] parts = referralData.split(";", -1);
        if (parts.length > 8) {
            StringBuilder displayText = new StringBuilder();
            displayText.append("Date: ").append(parts[1]).append("\n");
            displayText.append("Height: ").append(parts[2]).append(" cm\n");
            displayText.append("Weight: ").append(parts[3]).append(" kg\n");
            displayText.append("Blood Pressure: ").append(parts[4]).append("\n");
            displayText.append("Heart Rate: ").append(parts[5]).append(" bpm\n");
            displayText.append("Temperature: ").append(parts[6]).append(" °C\n");
            displayText.append("Concern: ").append(parts[7]).append("\n");
            displayText.append("Patient's Notes: ").append(parts[8].replace("\\n", "\n"));
            vitalsArea.setText(displayText.toString());
        } else {
            vitalsArea.setText("Could not load vital signs information.");
        }

        panel.add(new JScrollPane(vitalsArea), BorderLayout.CENTER);
        return panel;
    }

    private static void showAdminAddMenuDialog() {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available to add details to.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 1: Select a user
        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user to add details for:",
                "Add Details - Select User",
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user == null) {
            return; // Admin cancelled the user selection
        }

        // Step 2: Select the type of detail to add
        String[] addOptions = {
                "1. Add Medical Information (Allergies/Conditions)",
                "2. Add Medical History (with Verification Image)",
                "3. Back"
        };

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select an option for " + user.name + ":",
                "Add Menu",
                JOptionPane.PLAIN_MESSAGE,
                null,
                addOptions,
                addOptions[0]);

        if (choice == null || choice.contains("Back")) {
            return;
        }

        // Step 3: Call the appropriate method based on choice
        switch (choice.substring(0, 1)) {
            case "1" -> showAdminAddMedicalInfoDialog(user);
            case "2" -> showAdminAddMedicalHistoryDialog(user);
        }
    }

    /**
     * Displays a dialog for the Admin to add/manage a selected user's allergies and
     * medical conditions.
     * Reuses the panels from the Staff UI for consistency.
     * 
     * @param user The user whose information is being modified.
     */
    private static void showAdminAddMedicalInfoDialog(User user) {
        JDialog detailsDialog = new JDialog(mainFrame, "Add Medical Information for " + user.name, true);
        detailsDialog.setSize(900, 400);
        detailsDialog.setLocationRelativeTo(mainFrame);

        // We can reuse the same panels used by the Staff's "Add User Details" feature
        JPanel medicalInfoTab = createMedicalInfoTab(user, new JTextArea(), new JTextArea());

        detailsDialog.add(medicalInfoTab);
        detailsDialog.setVisible(true);
    }

    /**
     * Displays a dialog for the Admin to add medical history for a user,
     * including a verification image.
     * Reuses the panel from the Doctor's UI.
     * 
     * @param user The user whose history is being added.
     */
    private static void showAdminAddMedicalHistoryDialog(User user) {
        JDialog historyDialog = new JDialog(mainFrame, "Add Medical History for " + user.name, true);
        historyDialog.setSize(700, 450);
        historyDialog.setLocationRelativeTo(mainFrame);
        historyDialog.setLayout(new BorderLayout());

        // We can directly reuse the panel created for doctors
        JPanel historyPanel = createDoctorHistoryPanel(user);

        historyDialog.add(historyPanel, BorderLayout.CENTER);
        historyDialog.setVisible(true);
    }

    private static void showRemoveMenuDialog() {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] options = {
                "1. User Account",
                "2. User Detail",
                "3. User Medication",
                "4. User Medical History",
                "5. Back"
        };

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select an option:",
                "Remove Menu",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null || choice.endsWith("Back"))
            return;

        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user:",
                "Remove " + choice.substring(3),
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user == null)
            return;

        switch (choice.substring(0, 1)) {
            case "1" -> {
                int confirm = JOptionPane.showConfirmDialog(
                        mainFrame,
                        "Are you sure you want to remove this user account?",
                        "Confirm Removal",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    String deletedUsername = user.username;
                    int deletedUserId = user.id;

                    // 1. Remove user from the list
                    users.remove(user);

                    // 2. Update USER_DATABASE
                    try {
                        // Overwrite user database
                        FileWriter fw = new FileWriter(USER_DATABASE, false);
                        BufferedWriter bw = new BufferedWriter(fw);
                        PrintWriter out = new PrintWriter(bw);
                        for (User u : users) {
                            out.println(u.toStringForFile());
                        }
                        out.close();

                        // 3. Remove user's medication data
                        Path medPath = Paths.get(MEDICATION_DATABASE);
                        if (Files.exists(medPath)) {
                            List<String> lines = Files.readAllLines(medPath);
                            List<String> updatedLines = new ArrayList<>();
                            for (String line : lines) {
                                if (!line.startsWith(deletedUsername + ";")) {
                                    updatedLines.add(line);
                                }
                            }
                            Files.write(medPath, updatedLines);
                        }

                        // 4. Remove user's vitals data
                        Path vitalsPath = Paths.get(VITALS_DATABASE);
                        if (Files.exists(vitalsPath)) {
                            List<String> lines = Files.readAllLines(vitalsPath);
                            List<String> updatedLines = new ArrayList<>();
                            for (String line : lines) {
                                String[] parts = line.split(";");
                                if (parts.length == 0 || !parts[0].equals(String.valueOf(deletedUserId))) {
                                    updatedLines.add(line);
                                }
                            }
                            Files.write(vitalsPath, updatedLines);
                        }

                        JOptionPane.showMessageDialog(mainFrame, "User data removed!");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(mainFrame, "Error updating databases: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }

                    loadUsersFromFile();
                }
            }
            case "2" -> removeUserDetail(user);
            case "3" -> removeUserMedication(user);
            case "4" -> removeUserMedicalHistory(user);
        }
    }

    private static void removeUserDetail(User user) {
        String[] options = { "Allergies", "Medical Conditions" };
        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select detail to remove:",
                "Remove User Detail",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null)
            return;

        if (choice.equals("Allergies")) {
            if (user.allergies.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No allergies to remove.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String allergy = (String) JOptionPane.showInputDialog(
                    mainFrame,
                    "Select allergy to remove:",
                    "Remove Allergy",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    user.allergies.toArray(),
                    user.allergies.get(0));

            if (allergy != null) {
                user.allergies.remove(allergy);
                updateDatabaseFile(); // Add this line
                JOptionPane.showMessageDialog(mainFrame, "Allergy removed.");
            }
        } else {
            if (user.medicalConditions.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No medical conditions to remove.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String condition = (String) JOptionPane.showInputDialog(
                    mainFrame,
                    "Select medical condition to remove:",
                    "Remove Medical Condition",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    user.medicalConditions.toArray(),
                    user.medicalConditions.get(0));

            if (condition != null) {
                user.medicalConditions.remove(condition);
                updateDatabaseFile(); // Add this line
                JOptionPane.showMessageDialog(mainFrame, "Medical condition removed.");
            }
        }
    }

    private static boolean showMedicationTypeDialog() {
        String[] options = { "Current Medications", "Past Medications" };
        int choice = JOptionPane.showOptionDialog(
                mainFrame,
                "Select medication type:",
                "Medication Type",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        return choice == 1; // Returns true if "Past Medications" is selected
    }

    private static void removeUserMedication(User user) {
        // Let admin choose medication type
        boolean showPastMeds = showMedicationTypeDialog();

        // Load medications based on selection
        List<String[]> meds = loadMedicationsForUser(user.username, showPastMeds);

        if (meds.isEmpty()) {
            String msg = showPastMeds ? "No past medications found!" : "No current medications found!";
            JOptionPane.showMessageDialog(mainFrame, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create display strings
        String[] medStrings = new String[meds.size()];
        for (int i = 0; i < meds.size(); i++) {
            String[] med = meds.get(i);
            medStrings[i] = med[1] + " - " + med[2] + " (" + med[4] + " to " + med[5] + ")";
        }

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select medication to remove:",
                "Remove Medication",
                JOptionPane.PLAIN_MESSAGE,
                null,
                medStrings,
                medStrings[0]);

        if (choice != null) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(MEDICATION_DATABASE));
                List<String> updatedLines = new ArrayList<>();

                // Find and exclude the selected medication
                for (String line : lines) {
                    String[] parts = line.split(";", 6);
                    if (parts.length == 6 && parts[0].equals(user.username)) {
                        String lineDisplay = parts[1] + " - " + parts[2] + " (" + parts[4] + " to " + parts[5] + ")";
                        if (!lineDisplay.equals(choice)) {
                            updatedLines.add(line);
                        }
                    } else {
                        updatedLines.add(line);
                    }
                }

                Files.write(Paths.get(MEDICATION_DATABASE), updatedLines);
                JOptionPane.showMessageDialog(mainFrame, "Medication removed successfully!");

            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainFrame, "Error: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void removeUserMedicalHistory(User user) {
        if (user.medicalHistory.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No medical history to remove.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] historyStrings = new String[user.medicalHistory.size()];
        for (int i = 0; i < user.medicalHistory.size(); i++) {
            String[] history = user.medicalHistory.get(i);
            historyStrings[i] = history[0] + " - " + history[1];
        }

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select medical history to remove:",
                "Remove Medical History",
                JOptionPane.PLAIN_MESSAGE,
                null,
                historyStrings,
                historyStrings[0]);

        if (choice != null) {
            int index = -1;
            for (int i = 0; i < historyStrings.length; i++) {
                if (historyStrings[i].equals(choice)) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                user.medicalHistory.remove(index);
                updateDatabaseFile();
                JOptionPane.showMessageDialog(mainFrame, "Medical history removed.");
            }
        }
    }

    private static void showEditMenuDialog() {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] options = {
                "1. User Details (Allergies/Conditions)",
                "2. User Medication",
                "3. User Medical History",
                "4. Back"
        };

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select an option:",
                "Edit Menu",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null || choice.endsWith("Back"))
            return;

        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user:",
                "Edit " + choice.substring(3),
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user == null)
            return;

        switch (choice.substring(0, 1)) {
            case "1" -> editUserDetailsAdmin(user);
            case "2" -> editUserMedication(user);
            case "3" -> editUserMedicalHistory(user);
        }
    }

    private static void editUserDetailsAdmin(User user) {
        String[] options = { "Allergies", "Medical Conditions", "Back" };
        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select detail to edit:",
                "Edit User Details (Admin)",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null || choice.equals("Back"))
            return;

        if (choice.equals("Allergies")) {
            if (user.allergies.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No allergies to edit.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String allergy = (String) JOptionPane.showInputDialog(
                    mainFrame,
                    "Select allergy to edit:",
                    "Edit Allergy",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    user.allergies.toArray(),
                    user.allergies.get(0));

            if (allergy != null) {
                int index = user.allergies.indexOf(allergy);
                JPanel panel = new JPanel(new GridLayout(0, 1));

                // Create combo box for allergies
                JComboBox<String> allergyCombo = new JComboBox<>(COMMON_ALLERGIES);
                allergyCombo.setEditable(true);

                // Set current allergy (without severity and category)
                String currentAllergy = allergy.replaceAll("\\s*\\(.*\\)", "");
                // Find matching item in combo box
                boolean found = false;
                for (int i = 0; i < allergyCombo.getItemCount(); i++) {
                    String item = allergyCombo.getItemAt(i);
                    if (item.endsWith(currentAllergy)) {
                        allergyCombo.setSelectedIndex(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    allergyCombo.setSelectedItem("Other (specify)");
                    allergyCombo.setEditable(true);
                    allergyCombo.setSelectedItem(currentAllergy);
                }

                JComboBox<String> severityCombo = new JComboBox<>(new String[] { "mild", "severe" });

                // Set current severity
                if (allergy.contains("(mild)")) {
                    severityCombo.setSelectedItem("mild");
                } else if (allergy.contains("(severe)")) {
                    severityCombo.setSelectedItem("severe");
                }

                panel.add(new JLabel("Allergy:"));
                panel.add(allergyCombo);
                panel.add(new JLabel("Severity:"));
                panel.add(severityCombo);

                int result = JOptionPane.showConfirmDialog(
                        mainFrame,
                        panel,
                        "Edit Allergy",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    String newAllergy = (String) allergyCombo.getSelectedItem();
                    if (newAllergy != null && !newAllergy.trim().isEmpty()) {
                        // Handle "Other" selection
                        if (newAllergy.startsWith("Other (specify)")) {
                            String customAllergy = JOptionPane.showInputDialog(mainFrame,
                                    "Please specify the allergy:", "Custom Allergy", JOptionPane.PLAIN_MESSAGE);
                            if (customAllergy != null && !customAllergy.trim().isEmpty()) {
                                newAllergy = customAllergy.trim();
                            } else {
                                return; // User cancelled or entered nothing
                            }
                        } else if (newAllergy.contains(":")) {
                            newAllergy = newAllergy.substring(newAllergy.indexOf(":") + 1).trim();
                        }

                        String updatedAllergy = newAllergy + " (" + severityCombo.getSelectedItem() + ")";
                        user.allergies.set(index, updatedAllergy);
                        JOptionPane.showMessageDialog(mainFrame, "Allergy updated successfully!");
                        updateDatabaseFile();
                    }
                }
            }
        } else {
            if (user.medicalConditions.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No medical conditions to edit.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String condition = (String) JOptionPane.showInputDialog(
                    mainFrame,
                    "Select medical condition to edit:",
                    "Edit Medical Condition",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    user.medicalConditions.toArray(),
                    user.medicalConditions.get(0));

            if (condition != null) {
                int index = user.medicalConditions.indexOf(condition);

                // Create combo box for medical conditions
                JComboBox<String> conditionCombo = new JComboBox<>(COMMON_MEDICAL_CONDITIONS);
                conditionCombo.setEditable(true);

                // Find matching item in combo box
                boolean found = false;
                for (int i = 0; i < conditionCombo.getItemCount(); i++) {
                    String item = conditionCombo.getItemAt(i);
                    if (item.endsWith(condition)) {
                        conditionCombo.setSelectedIndex(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    conditionCombo.setSelectedItem("Other (specify)");
                    conditionCombo.setEditable(true);
                    conditionCombo.setSelectedItem(condition);
                }

                int result = JOptionPane.showConfirmDialog(
                        mainFrame,
                        conditionCombo,
                        "Edit Medical Condition",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    String newCondition = (String) conditionCombo.getSelectedItem();
                    if (newCondition != null && !newCondition.isEmpty()) {
                        // Handle "Other" selection
                        if (newCondition.startsWith("Other (specify)")) {
                            String customCondition = JOptionPane.showInputDialog(mainFrame,
                                    "Please specify the condition:", "Custom Condition", JOptionPane.PLAIN_MESSAGE);
                            if (customCondition != null && !customCondition.trim().isEmpty()) {
                                newCondition = customCondition.trim();
                            } else {
                                return; // User cancelled or entered nothing
                            }
                        } else if (newCondition.contains(":")) {
                            newCondition = newCondition.substring(newCondition.indexOf(":") + 1).trim();
                        }

                        user.medicalConditions.set(index, newCondition);
                        JOptionPane.showMessageDialog(mainFrame, "Medical condition updated successfully!");
                        updateDatabaseFile();
                    }
                }
            }
        }
    }

    private static void editUserMedication(User user) {

        boolean showPastMeds = showMedicationTypeDialog();

        // Load medications based on selection
        List<String[]> meds = loadMedicationsForUser(user.username, showPastMeds);

        if (meds.isEmpty()) {
            String msg = showPastMeds ? "No past medications found!" : "No current medications found!";
            JOptionPane.showMessageDialog(mainFrame, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Check if medication database exists
        File file = new File(MEDICATION_DATABASE);
        if (!file.exists() || file.length() == 0) {
            JOptionPane.showMessageDialog(mainFrame, "No medications found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create display strings for selection
        String[] medStrings = new String[meds.size()];
        for (int i = 0; i < meds.size(); i++) {
            String[] med = meds.get(i);
            medStrings[i] = med[1] + " - " + med[2] + " - " + med[4] + " to " + med[5];
        }

        // Let user select a medication to edit
        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select medication to edit:",
                "Edit Medication",
                JOptionPane.PLAIN_MESSAGE,
                null,
                medStrings,
                medStrings[0]);

        if (choice == null)
            return; // User canceled

        int index = -1;
        for (int i = 0; i < medStrings.length; i++) {
            if (medStrings[i].equals(choice)) {
                index = i;
                break;
            }
        }

        if (index == -1)
            return;

        // Load selected medication data
        String[] med = meds.get(index);
        JTextField nameField = new JTextField(med[1]);
        JTextField doseField = new JTextField(med[2]);
        JTextField instructionsField = new JTextField(med[3]);
        JTextField startField = new JTextField(med[4]);
        JTextField endField = new JTextField(med[5]);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Medication Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Dosage:"));
        panel.add(doseField);
        panel.add(new JLabel("Instructions:"));
        panel.add(instructionsField);
        panel.add(new JLabel("Start Date (MM/DD/YYYY HH:mm):"));
        panel.add(startField);
        panel.add(new JLabel("End Date (MM/DD/YYYY HH:mm):"));
        panel.add(endField);

        int result = JOptionPane.showConfirmDialog(
                mainFrame, panel, "Edit Medication", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            // Update the medication entry
            String updatedLine = String.join(";",
                    user.username,
                    nameField.getText().trim(),
                    doseField.getText().trim(),
                    instructionsField.getText().trim(),
                    startField.getText().trim(),
                    endField.getText().trim());

            try {
                // Read all lines
                List<String> lines = Files.readAllLines(Paths.get(MEDICATION_DATABASE));

                // Find and replace the line
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).startsWith(user.username + ";" + med[1])) {
                        lines.set(i, updatedLine);
                        break;
                    }
                }

                // Write back to file
                Files.write(Paths.get(MEDICATION_DATABASE), lines);
                JOptionPane.showMessageDialog(mainFrame, "Medication updated!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainFrame, "Error updating medication:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void editUserMedicalHistory(User user) {
        if (user.medicalHistory.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No medical history to edit.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create display strings for medical history (NOT medications)
        String[] historyStrings = new String[user.medicalHistory.size()];
        for (int i = 0; i < user.medicalHistory.size(); i++) {
            String[] history = user.medicalHistory.get(i);
            historyStrings[i] = history[0] + " - " + history[1];
        }

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select medical history to edit:",
                "Edit Medical History",
                JOptionPane.PLAIN_MESSAGE,
                null,
                historyStrings,
                historyStrings[0]);

        if (choice == null)
            return;

        // Find the selected history entry
        int index = -1;
        for (int i = 0; i < historyStrings.length; i++) {
            if (historyStrings[i].equals(choice)) {
                index = i;
                break;
            }
        }
        if (index == -1)
            return;

        // Edit UI
        String[] history = user.medicalHistory.get(index);
        JTextField historyField = new JTextField(history[0]);
        JTextField dateField = new JTextField(history[1]);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Medical History:"));
        panel.add(historyField);
        panel.add(new JLabel("Date (MM/DD/YYYY):"));
        panel.add(dateField);

        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                panel,
                "Edit Medical History",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            // Update the medical history entry
            user.medicalHistory.set(index, new String[] {
                    historyField.getText().trim(),
                    dateField.getText().trim()
            });

            // Save to USER database (not medication database)
            updateDatabaseFile(); // This updates USER_DATABASE
            JOptionPane.showMessageDialog(mainFrame, "Medical history updated!");
        }
    }

    private static void showProfilesDialog() {
        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No users available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        User user = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select a user:",
                "View Profile",
                JOptionPane.PLAIN_MESSAGE,
                null,
                users.toArray(),
                users.get(0));

        if (user == null)
            return;

        // Create a tabbed pane with 4 tabs
        JTabbedPane tabbedPane = new JTabbedPane();

        // === TAB 1: PROFILE ===
        JPanel profilePanel = new JPanel(new BorderLayout());
        JPanel profileContentPanel = new JPanel(new BorderLayout(10, 10));
        profileContentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setPreferredSize(new Dimension(200, 200));
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        if (user.imagePath != null && !user.imagePath.isEmpty()) {
            try {
                File imageFile = new File(user.imagePath);
                if (imageFile.exists()) {
                    ImageIcon icon = new ImageIcon(user.imagePath);
                    Image image = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                    imageLabel.setIcon(new ImageIcon(image));
                } else {
                    imageLabel.setText("Image not found");
                }
            } catch (Exception e) {
                imageLabel.setText("Image not available");
            }
        } else {
            imageLabel.setText("No image");
        }
        imagePanel.add(imageLabel, BorderLayout.CENTER);
        profileContentPanel.add(imagePanel, BorderLayout.EAST);

        JTextArea profileText = new JTextArea();
        profileText.setEditable(false);
        profileText.setFont(new Font("Arial", Font.PLAIN, 14));
        profileText.append("Name: " + user.name + "\n\n");
        profileText.append("Age: " + user.age + "\n\n");
        profileText.append("Birthday: " + user.birthday + "\n\n");
        profileText.append("Civil Status: " + user.civilStatus + "\n\n");
        profileText.append("Blood Type: " + user.bloodType + "\n\n");
        profileText.append("Contact Number: " + user.contactNumber + "\n\n");
        profileText.append("Emergency Contact: " + user.emergencyContactNumber + "\n\n");
        profileText.append("Allergies: " + String.join(", ", user.allergies) + "\n\n");
        profileText.append("Medical Conditions: " + String.join(", ", user.medicalConditions) + "\n");

        JScrollPane textScroll = new JScrollPane(profileText);
        textScroll.setPreferredSize(new Dimension(300, 400));
        profileContentPanel.add(textScroll, BorderLayout.CENTER);

        profilePanel.add(profileContentPanel, BorderLayout.CENTER);
        tabbedPane.addTab("Profile", profilePanel);

        // === TAB 2: MEDICAL HISTORY ===
        JPanel historyPanel = new JPanel(new BorderLayout());
        JTextArea historyText = new JTextArea();
        historyText.setEditable(false);
        historyText.setFont(new Font("Arial", Font.PLAIN, 14));
        if (user.medicalHistory.isEmpty()) {
            historyText.append("No medical history available.");
        } else {
            for (String[] history : user.medicalHistory) {
                historyText.append("• " + history[0] + "\n");
                historyText.append("  Occurred: " + history[1] + "\n\n");
            }
        }
        historyPanel.add(new JScrollPane(historyText), BorderLayout.CENTER);
        tabbedPane.addTab("Medical History", historyPanel);

        // === TAB 3: MEDICATION TAKEN CURRENTLY ===
        JPanel currentMedPanel = new JPanel(new BorderLayout());
        JTextArea currentMedArea = new JTextArea("Loading...");
        currentMedArea.setEditable(false);
        currentMedArea.setFont(new Font("Arial", Font.PLAIN, 14));
        currentMedPanel.add(new JScrollPane(currentMedArea), BorderLayout.CENTER);
        tabbedPane.addTab("Medication Taken Currently", currentMedPanel);

        // === TAB 4: MEDICATION TAKEN HISTORY ===
        JPanel pastMedPanel = new JPanel(new BorderLayout());
        JTextArea pastMedArea = new JTextArea("Loading...");
        pastMedArea.setEditable(false);
        pastMedArea.setFont(new Font("Arial", Font.PLAIN, 14));
        pastMedPanel.add(new JScrollPane(pastMedArea), BorderLayout.CENTER);
        tabbedPane.addTab("Medication Taken History", pastMedPanel);

        // Load data from medication.txt
        loadMedicationData(currentMedArea, pastMedArea, user.username);

        // Final dialog setup
        JDialog dialog = new JDialog(mainFrame, "User Profile - " + user.name, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(tabbedPane);
        dialog.pack();
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setVisible(true);
    }

    private static void showUserProfile() {
        if (currentUser == null)
            return;

        JDialog dialog = new JDialog(mainFrame, "Your Profile", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel profilePanel = new JPanel(new BorderLayout(10, 10));
        profilePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left side: text info
        JTextArea profileText = new JTextArea();
        profileText.setEditable(false);
        profileText.setFont(new Font("Arial", Font.PLAIN, 14));
        profileText.append("Name: " + currentUser.name + "\n\n");
        profileText.append("Age: " + currentUser.age + "\n\n");
        profileText.append("Birthday: " + currentUser.birthday + "\n\n");
        profileText.append("Civil Status: " + currentUser.civilStatus + "\n\n");
        profileText.append("Blood Type: " + currentUser.bloodType + "\n\n");
        profileText.append("Contact Number: " + currentUser.contactNumber + "\n\n");
        profileText.append("Emergency Contact: " + currentUser.emergencyContactNumber + "\n\n");
        profileText.append("Allergies: " + String.join(", ", currentUser.allergies) + "\n\n");
        profileText.append("Medical Conditions: " + String.join(", ", currentUser.medicalConditions) + "\n");

        profilePanel.add(new JScrollPane(profileText), BorderLayout.CENTER);

        // Right side: image preview
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setPreferredSize(new Dimension(160, 200));
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        if (currentUser.imagePath != null && !currentUser.imagePath.isEmpty()) {
            File imgFile = new File(currentUser.imagePath);
            if (imgFile.exists()) {
                ImageIcon icon = new ImageIcon(currentUser.imagePath);
                Image img = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(img));
            } else {
                imageLabel.setText("Image not found");
            }
        } else {
            imageLabel.setText("No image");
        }
        imagePanel.add(imageLabel, BorderLayout.NORTH);
        profilePanel.add(imagePanel, BorderLayout.EAST);

        dialog.add(profilePanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static void showUserUpdateDialog(Component parent) {
        String[] options = {
                "1. Update Civil Status",
                "2. Update Contact Number",
                "3. Update Emergency Contact",
                "4. Update Profile Picture"
        };

        String choice = (String) JOptionPane.showInputDialog(
                parent,
                "Select what to update:",
                "Update Information",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null)
            return;

        switch (choice.charAt(0)) {
            case '1' -> {
                String newStatus = JOptionPane.showInputDialog(parent, "Enter new Civil Status (Single/Married):",
                        currentUser.civilStatus);
                if (newStatus != null
                        && (newStatus.equalsIgnoreCase("Single") || newStatus.equalsIgnoreCase("Married"))) {
                    currentUser.civilStatus = newStatus;
                    updateDatabaseFile();
                    JOptionPane.showMessageDialog(parent, "Civil status updated.");
                } else {
                    JOptionPane.showMessageDialog(parent, "Invalid status. Must be Single or Married.");
                }
            }
            case '2' -> {
                String newContact = JOptionPane.showInputDialog(parent, "Enter new Contact Number (XXXX-XXX-XXXX):",
                        currentUser.contactNumber);
                if (newContact != null && newContact.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                    currentUser.contactNumber = newContact;
                    updateDatabaseFile();
                    JOptionPane.showMessageDialog(parent, "Contact number updated.");
                } else {
                    JOptionPane.showMessageDialog(parent, "Invalid format.");
                }
            }
            case '3' -> {
                String newEmergency = JOptionPane.showInputDialog(parent,
                        "Enter new Emergency Contact (XXXX-XXX-XXXX):", currentUser.emergencyContactNumber);
                if (newEmergency != null && newEmergency.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                    currentUser.emergencyContactNumber = newEmergency;
                    updateDatabaseFile();
                    JOptionPane.showMessageDialog(parent, "Emergency contact updated.");
                } else {
                    JOptionPane.showMessageDialog(parent, "Invalid format.");
                }
            }
            case '4' -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Select Profile Picture");
                fileChooser.setAcceptAllFileFilterUsed(false);
                fileChooser.addChoosableFileFilter(
                        new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes()));

                if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    currentUser.imagePath = file.getAbsolutePath();
                    updateDatabaseFile();
                    JOptionPane.showMessageDialog(parent, "Profile picture updated.");
                }
            }
        }
    }

    private static void showMasterlistDialog(boolean showAll) {
        JDialog masterlistDialog = new JDialog(mainFrame, "Masterlist", true);
        masterlistDialog.setSize(900, 600);
        masterlistDialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: All Users (or just Patients if not showAll)
        String tabTitle = showAll ? "All Users" : "Patients";
        tabbedPane.addTab(tabTitle, createUserListPanel(users));

        if (showAll) {
            // Only show these tabs if it's the admin view
            List<User> staffList = loadStaffFromFile();
            tabbedPane.addTab("Staff", createUserListPanel(staffList));

            List<User> doctorsList = loadDoctorsFromFile();
            tabbedPane.addTab("Doctors", createUserListPanel(doctorsList));
        }

        masterlistDialog.add(tabbedPane);
        masterlistDialog.setVisible(true);
    }

    private static JPanel createUserListPanel(List<User> userList) {
        JPanel panel = new JPanel(new BorderLayout());

        // Create column names
        String[] columnNames = { "ID", "Name", "Username", "Contact", "Type" };

        // Create data for the table
        Object[][] data = new Object[userList.size()][5];
        for (int i = 0; i < userList.size(); i++) {
            User user = userList.get(i);
            data[i][0] = user.id;
            data[i][1] = user.name;
            data[i][2] = user.username;
            data[i][3] = user.contactNumber;
            data[i][4] = user.profession != null ? user.profession : "Patient";
        }

        // Create the table
        JTable table = new JTable(data, columnNames);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        // Add the table to a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);

        // Add search functionality
        JPanel searchPanel = new JPanel(new BorderLayout());
        JTextField searchField = new JTextField();
        JButton searchButton = new JButton("Search");

        searchButton.addActionListener(e -> {
            String searchText = searchField.getText().toLowerCase();
            TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
            table.setRowSorter(sorter);

            if (searchText.trim().length() == 0) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
            }
        });

        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void showStaffManageAccountDialog() {
        JDialog dialog = new JDialog(mainFrame, "Manage Account", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Personal Info
        JPanel personalInfoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        personalInfoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField contactField = new JTextField(currentUser.contactNumber);
        JComboBox<String> civilStatusCombo = new JComboBox<>(new String[] { "Single", "Married" });
        civilStatusCombo.setSelectedItem(currentUser.civilStatus);

        personalInfoPanel.add(new JLabel("Contact Number (XXXX-XXX-XXXX):"));
        personalInfoPanel.add(contactField);
        personalInfoPanel.add(new JLabel("Civil Status:"));
        personalInfoPanel.add(civilStatusCombo);

        JButton savePersonalButton = new JButton("Save Changes");
        savePersonalButton.addActionListener(e -> {
            if (contactField.getText().matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                currentUser.contactNumber = contactField.getText();
                currentUser.civilStatus = (String) civilStatusCombo.getSelectedItem();
                updateStaffRecord();
                JOptionPane.showMessageDialog(dialog, "Personal info updated!");
            } else {
                JOptionPane.showMessageDialog(dialog, "Invalid contact format!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        personalInfoPanel.add(savePersonalButton);
        tabbedPane.addTab("Personal Info", personalInfoPanel);

        // Tab 2: Account Security
        JPanel securityPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        securityPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField usernameField = new JTextField(currentUser.username);
        JPasswordField passwordField = new JPasswordField();
        passwordField.setText(currentUser.password);

        securityPanel.add(new JLabel("Username:"));
        securityPanel.add(usernameField);
        securityPanel.add(new JLabel("Password (12 digits):"));
        securityPanel.add(passwordField);

        JButton saveSecurityButton = new JButton("Save Changes");
        saveSecurityButton.addActionListener(e -> {
            if (new String(passwordField.getPassword()).length() == 12) {
                currentUser.username = usernameField.getText();
                currentUser.password = new String(passwordField.getPassword());
                updateStaffRecord();
                JOptionPane.showMessageDialog(dialog, "Account security updated!");
            } else {
                JOptionPane.showMessageDialog(dialog, "Password must be 12 digits!", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        securityPanel.add(saveSecurityButton);
        tabbedPane.addTab("Account Security", securityPanel);

        dialog.add(tabbedPane, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private static void updateStaffRecord() {
        try {
            List<User> staffList = loadStaffFromFile();
            staffList.removeIf(s -> s.username.equals(currentUser.username));
            staffList.add(currentUser);
            saveAllStaffToFile(staffList);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(mainFrame, "Error updating record: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JPanel createStaffInfoUpdatePanel(JDialog parentDialog) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Contact Number ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Contact Number (XXXX-XXX-XXXX):"), gbc);
        gbc.gridx = 1;
        JTextField contactField = new JTextField(currentUser.contactNumber, 15); // Pre-fill with current data
        panel.add(contactField, gbc);

        // --- Civil Status ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Civil Status:"), gbc);
        gbc.gridx = 1;
        // JComboBox is better than a text field for fixed options
        JComboBox<String> civilStatusCombo = new JComboBox<>(new String[] { "Single", "Married" });
        civilStatusCombo.setSelectedItem(currentUser.civilStatus); // Pre-select current status
        panel.add(civilStatusCombo, gbc);

        // --- Save Button ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton saveButton = new JButton("Save Information");
        panel.add(saveButton, gbc);

        // --- Action Logic ---
        saveButton.addActionListener(e -> {
            String newContact = contactField.getText().trim();
            String newStatus = (String) civilStatusCombo.getSelectedItem();

            // Validation
            if (!newContact.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                JOptionPane.showMessageDialog(panel, "Invalid contact number format. Use XXXX-XXX-XXXX.", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Update the currently logged-in user object in memory
            currentUser.contactNumber = newContact;
            currentUser.civilStatus = newStatus;

            // Save the changes to the staff.txt file
            try {
                List<User> staffList = loadStaffFromFile(); // Load all staff
                for (int i = 0; i < staffList.size(); i++) {
                    // Find the current user in the list by their username
                    if (staffList.get(i).username.equals(currentUser.username)) {
                        staffList.set(i, currentUser); // Replace the old record with the updated one
                        break;
                    }
                }
                saveAllStaffToFile(staffList); // Save the entire updated list back to the file
                JOptionPane.showMessageDialog(panel, "Information updated successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                parentDialog.dispose(); // Close the dialog
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(panel, "Error saving data: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    private static JPanel createStaffCredentialsUpdatePanel(JDialog parentDialog) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- New Username ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("New Username:"), gbc);
        gbc.gridx = 1;
        JTextField usernameField = new JTextField(15);
        panel.add(usernameField, gbc);

        // --- Confirm New Username ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Confirm New Username:"), gbc);
        gbc.gridx = 1;
        JTextField confirmUsernameField = new JTextField(15);
        panel.add(confirmUsernameField, gbc);

        // --- New Password ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("New Password (12 digits):"), gbc);
        gbc.gridx = 1;
        JPasswordField passwordField = new JPasswordField(15);
        panel.add(passwordField, gbc);

        // --- Confirm New Password ---
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("Confirm New Password:"), gbc);
        gbc.gridx = 1;
        JPasswordField confirmPasswordField = new JPasswordField(15);
        panel.add(confirmPasswordField, gbc);

        // --- Save Button ---
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton saveButton = new JButton("Save Credentials");
        panel.add(saveButton, gbc);

        // --- Action Logic ---
        saveButton.addActionListener(e -> {
            String newUsername = usernameField.getText().trim();
            String confirmUsername = confirmUsernameField.getText().trim();
            String newPassword = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            // Store the original username to find the record in the file
            String oldUsername = currentUser.username;

            // --- Validation ---
            if (!newUsername.isEmpty()) { // Only validate if user is trying to change username
                if (!newUsername.equals(confirmUsername)) {
                    JOptionPane.showMessageDialog(panel, "Usernames do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Check if username is already taken by any user type
                List<User> allUsers = new ArrayList<>(users); // From users.txt
                allUsers.addAll(loadStaffFromFile()); // From staff.txt
                allUsers.addAll(loadDoctorsFromFile()); // From doctors.txt
                for (User u : allUsers) {
                    if (u.username.equals(newUsername)) {
                        JOptionPane.showMessageDialog(panel, "Username already exists. Please choose another.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
            }

            if (!newPassword.isEmpty()) { // Only validate if user is trying to change password
                if (newPassword.length() != 12) {
                    JOptionPane.showMessageDialog(panel, "Password must be exactly 12 digits.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!newPassword.equals(confirmPassword)) {
                    JOptionPane.showMessageDialog(panel, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            if (newUsername.isEmpty() && newPassword.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "No changes to save.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // --- Update and Save ---
            if (!newUsername.isEmpty()) {
                currentUser.username = newUsername;
            }
            if (!newPassword.isEmpty()) {
                currentUser.password = newPassword;
            }

            try {
                List<User> staffList = loadStaffFromFile();
                for (int i = 0; i < staffList.size(); i++) {
                    // Find the record using the OLD username
                    if (staffList.get(i).username.equals(oldUsername)) {
                        staffList.set(i, currentUser); // Update with new information
                        break;
                    }
                }
                saveAllStaffToFile(staffList);
                JOptionPane.showMessageDialog(panel, "Credentials updated successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                parentDialog.dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(panel, "Error saving data: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    private static void showManageStaffDialog() {
        String[] options = {
                "1. Add Doctor Account",
                "2. Add Medical Staff",
                "3. Edit Medical Staff",
                "4. Remove Medical Staff",
                "5. Back"
        };

        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select staff management option:",
                "Manage Staff",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null || choice.contains("Back"))
            return;

        switch (choice.substring(0, 1)) {
            case "1" -> showAddDoctorDialog();
            case "2" -> addMedicalStaff();
            case "3" -> editMedicalStaff();
            case "4" -> removeMedicalStaff();
        }
    }

    private static void showAddDoctorDialog() {

        JDialog dialog = new JDialog(mainFrame, "Add Doctor Account", true);
        dialog.setLayout(new BorderLayout());
        dialog.setPreferredSize(new Dimension(1000, 600));
        dialog.setLocationRelativeTo(mainFrame);

        // Main content panel
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 20, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ================== LEFT PANEL ==================
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0; // Initialize row counter

        // Name Field
        JTextField nameField = new JTextField(25);
        addField(leftPanel, gbc, "Name (First, Last, MI):", nameField, row++);

        JTextField birthdayField = new JTextField(25);
        addField(leftPanel, gbc, "Birthday (MM/DD/YYYY):", birthdayField, row++);

        // Age Components (Auto-calculated)
        JTextField ageField = new JTextField(25);
        ageField.setEditable(false);
        addField(leftPanel, gbc, "Age:", ageField, row++);

        // ADD THIS LINE (Critical fix)
        setupAutoAgeCalculation(birthdayField, ageField);

        // Civil Status
        JComboBox<String> civilStatusCombo = new JComboBox<>(new String[] { "Single", "Married" });
        addField(leftPanel, gbc, "Civil Status:", civilStatusCombo, row++);

        // Blood Type
        JTextField bloodTypeField = new JTextField(25);
        addField(leftPanel, gbc, "Blood Type:", bloodTypeField, row++);

        // Contact Number
        JTextField contactField = new JTextField(25);
        addField(leftPanel, gbc, "Contact Number:", contactField, row++);

        contentPanel.add(leftPanel);

        // ================== RIGHT PANEL ==================
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.insets = new Insets(10, 10, 10, 10);
        rightGbc.anchor = GridBagConstraints.WEST;
        rightGbc.fill = GridBagConstraints.HORIZONTAL;

        int rightRow = 0;

        // Specialty Field
        JTextField specialtyField = new JTextField(25);
        addField(rightPanel, rightGbc, "Specialty:", specialtyField, rightRow++);

        // Username
        JTextField usernameField = new JTextField(25);
        addField(rightPanel, rightGbc, "Username:", usernameField, rightRow++);

        // Password
        JPasswordField passwordField = new JPasswordField(25);
        addField(rightPanel, rightGbc, "Password (12 digits):", passwordField, rightRow++);

        // Image Preview
        JLabel imagePreview = new JLabel();
        imagePreview.setPreferredSize(new Dimension(200, 200));
        imagePreview.setHorizontalAlignment(JLabel.CENTER);
        imagePreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        final String[] imagePath = new String[1];

        // Upload Button
        JButton uploadButton = new JButton("Add Picture");
        uploadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Profile Picture");
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.addChoosableFileFilter(
                    new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes()));

            if (fileChooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    File selectedFile = fileChooser.getSelectedFile();
                    BufferedImage img = ImageIO.read(selectedFile);
                    Image scaledImg = img.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                    imagePreview.setIcon(new ImageIcon(scaledImg));
                    imagePath[0] = selectedFile.getAbsolutePath();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "Error loading image: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        rightGbc.gridx = 0;
        rightGbc.gridy = rightRow++;
        rightGbc.gridwidth = 2;
        rightPanel.add(imagePreview, rightGbc);

        rightGbc.gridy = rightRow++;
        rightPanel.add(uploadButton, rightGbc);

        contentPanel.add(rightPanel);

        // ================== BUTTONS ==================
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Register Doctor");
        JButton cancelButton = new JButton("Cancel");

        // in private static void showAddDoctorDialog()

        saveButton.addActionListener(e -> {
            // Validation
            if (validateDoctorInput(nameField, birthdayField, ageField, bloodTypeField,
                    contactField, specialtyField, usernameField, passwordField)) {

                // --- MODIFICATION START ---
                // ID Generation for Doctors
                List<User> doctorList = loadDoctorsFromFile();
                int nextDoctorId = doctorList.stream().mapToInt(d -> d.id).max().orElse(0) + 1;
                // --- MODIFICATION END ---

                User doctor = new User();
                doctor.id = nextDoctorId; // Assign the new unique ID
                doctor.name = nameField.getText();
                doctor.birthday = birthdayField.getText();
                doctor.age = Integer.parseInt(ageField.getText());
                doctor.bloodType = bloodTypeField.getText();
                doctor.civilStatus = (String) civilStatusCombo.getSelectedItem();
                doctor.contactNumber = contactField.getText();
                doctor.username = usernameField.getText();
                doctor.password = new String(passwordField.getPassword());
                doctor.profession = specialtyField.getText();
                doctor.imagePath = imagePath[0];

                try {
                    saveDoctorToFile(doctor);
                    JOptionPane.showMessageDialog(dialog, "Doctor registered successfully!");
                    dialog.dispose();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "Error saving doctor: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setVisible(true);
    }

    // Helper method for validation
    private static boolean validateDoctorInput(JTextField... fields) {
        for (JTextField field : fields) {
            if (field.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "All fields are required!", "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        return true;
    }

    // Save doctor to doctors.txt
    private static void saveDoctorToFile(User doctor) throws IOException {
        Path path = Paths.get(DOCTORS_DATABASE);
        String data = doctor.toStringForFile();
        Files.write(path, (data + System.lineSeparator()).getBytes(), StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    // Helper method to create bold labels
    private static JLabel createBoldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static List<User> loadStaffFromFile() {
        List<User> staffList = new ArrayList<>();
        try {
            Path path = Paths.get(STAFF_DATABASE);
            System.out.println("[DEBUG] Loading staff from: " + path.toAbsolutePath());

            if (!Files.exists(path)) {
                System.out.println("[WARN] Staff file not found");
                return staffList;
            }

            // Read all lines with explicit UTF-8 encoding
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            System.out.println("[DEBUG] Raw staff.txt content:");
            lines.forEach(System.out::println);

            // Process each line with error handling
            for (String line : lines) {
                if (line.trim().isEmpty())
                    continue; // Skip empty lines

                try {
                    User staff = User.fromString(line);
                    if (staff != null
                            && staff.profession != null
                            && !staff.profession.isEmpty()) {
                        staffList.add(staff);
                        System.out.println("[DEBUG] Loaded staff: " + staff.name);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to parse line: " + line);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            String errorMsg = "Error loading staff data: " + e.getMessage();
            System.err.println("[ERROR] " + errorMsg);
            JOptionPane.showMessageDialog(
                    null,
                    errorMsg,
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
        return staffList;
    }

    private static void showStaffProfileDialog(User staff) {
        JDialog dialog = new JDialog(mainFrame, "Staff Profile - " + staff.name, true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel profilePanel = new JPanel(new BorderLayout(10, 10));
        profilePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Text Info
        JTextArea profileText = new JTextArea();
        profileText.setEditable(false);
        profileText.setFont(new Font("Arial", Font.PLAIN, 14));
        profileText.append("Name: " + staff.name + "\n\n");
        profileText.append("Age: " + staff.age + "\n\n");
        profileText.append("Profession: " + staff.profession + "\n\n");
        profileText.append("Birthday: " + staff.birthday + "\n\n");
        profileText.append("Blood Type: " + staff.bloodType + "\n\n");
        profileText.append("Civil Status: " + staff.civilStatus + "\n\n");
        profileText.append("Contact Number: " + staff.contactNumber + "\n");

        // Image Preview
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setPreferredSize(new Dimension(150, 150));
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        if (staff.imagePath != null && !staff.imagePath.isEmpty()) {
            try {
                ImageIcon icon = new ImageIcon(staff.imagePath);
                Image image = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(image));
            } catch (Exception e) {
                imageLabel.setText("Image not available");
            }
        } else {
            imageLabel.setText("No image");
        }
        imagePanel.add(imageLabel, BorderLayout.CENTER);

        profilePanel.add(new JScrollPane(profileText), BorderLayout.CENTER);
        profilePanel.add(imagePanel, BorderLayout.EAST);

        // Close Button
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());

        dialog.add(profilePanel, BorderLayout.CENTER);
        dialog.add(closeButton, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static void addMedicalStaff() {
        final String[] selectedImagePath = { null };
        JDialog staffDialog = new JDialog(mainFrame, "Register Medical Staff", true);
        staffDialog.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Title
        JLabel titleLabel = new JLabel("Medical Staff Registration", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Main Content Panel
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 40, 10));

        // ================== LEFT PANEL ==================
        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // Name Components
        JTextField nameField = new JTextField(25);
        addField(leftPanel, gbc, "Name (First, Last, MI):", nameField, row++);

        // Birthday Components
        JTextField birthdayField = new JTextField(25);
        addField(leftPanel, gbc, "Birthday (MM/DD/YYYY):", birthdayField, row++);

        // Age Components (Auto-calculated)
        JTextField ageField = new JTextField(25);
        ageField.setEditable(false);
        addField(leftPanel, gbc, "Age:", ageField, row++);

        // Setup auto age calculation
        setupAutoAgeCalculation(birthdayField, ageField);

        // Civil Status Components
        JComboBox<String> civilStatusCombo = new JComboBox<>(new String[] { "Single", "Married" });
        addField(leftPanel, gbc, "Civil Status:", civilStatusCombo, row++);

        // Blood Type Components
        JTextField bloodTypeField = new JTextField(25);
        addField(leftPanel, gbc, "Blood Type:", bloodTypeField, row++);

        // Contact Number Components
        JTextField contactNumberField = new JTextField(25);
        addField(leftPanel, gbc, "Contact Number (Format: XXXX-XXX-XXXX):", contactNumberField, row++);

        // Profession Components
        JComboBox<String> professionCombo = new JComboBox<>(MEDICAL_PROFESSIONS);
        addField(leftPanel, gbc, "Profession:", professionCombo, row++);

        // ================== RIGHT PANEL ==================
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.insets = new Insets(10, 10, 10, 10);
        rightGbc.anchor = GridBagConstraints.WEST;
        rightGbc.fill = GridBagConstraints.HORIZONTAL;

        // Username
        JTextField usernameField = new JTextField(25);
        addField(rightPanel, rightGbc, "Username:", usernameField, 0);

        // Password
        JPasswordField passwordField = new JPasswordField(25);
        addField(rightPanel, rightGbc, "Password (12 digits):", passwordField, 1);

        // Image Components
        JLabel imagePreview = new JLabel();
        imagePreview.setPreferredSize(new Dimension(200, 200));
        imagePreview.setHorizontalAlignment(JLabel.CENTER);
        imagePreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JButton addImageButton = new JButton("Add Picture");
        addImageButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Profile Picture");
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.addChoosableFileFilter(
                    new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes()));

            if (fileChooser.showOpenDialog(staffDialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    File selectedFile = fileChooser.getSelectedFile();
                    BufferedImage originalImage = ImageIO.read(selectedFile);
                    Image scaledImage = originalImage.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                    imagePreview.setIcon(new ImageIcon(scaledImage));
                    selectedImagePath[0] = selectedFile.getAbsolutePath();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(staffDialog,
                            "Error loading image: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        rightGbc.gridx = 0;
        rightGbc.gridy = 2;
        rightGbc.gridwidth = 2;
        rightPanel.add(imagePreview, rightGbc);

        rightGbc.gridy = 3;
        rightPanel.add(addImageButton, rightGbc);

        contentPanel.add(leftPanel);
        contentPanel.add(rightPanel);

        // ================== BUTTON PANEL ==================
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton registerButton = new JButton("Register Staff");
        JButton cancelButton = new JButton("Cancel");

        registerButton.addActionListener(e -> {
            // Validation logic
            boolean isValid = true;

            // Name validation
            String name = nameField.getText().trim();
            if (!name.matches("[A-Za-z]+(\\s[A-Za-z]+)+")) {
                JOptionPane.showMessageDialog(staffDialog, "Invalid name format", "Error", JOptionPane.ERROR_MESSAGE);
                isValid = false;
            }

            // Age validation (auto-filled but need to check validity)
            if (ageField.getText().isEmpty()) {
                JOptionPane.showMessageDialog(staffDialog, "Invalid birthday", "Error", JOptionPane.ERROR_MESSAGE);
                isValid = false;
            }

            // Blood type validation
            String bloodType = bloodTypeField.getText().trim().toUpperCase();
            if (!bloodType.matches("^(A|B|AB|O)[+-]$")) {
                JOptionPane.showMessageDialog(staffDialog, "Invalid blood type", "Error", JOptionPane.ERROR_MESSAGE);
                isValid = false;
            }

            // Contact number validation
            String contactNumber = contactNumberField.getText().trim();
            if (!contactNumber.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                JOptionPane.showMessageDialog(staffDialog, 
                    "Invalid contact format. Must be exactly: XXXX-XXX-XXXX (11 digits)", 
                    "Error", JOptionPane.ERROR_MESSAGE);
                isValid = false;
            }

            // Password validation
            if (new String(passwordField.getPassword()).length() != 12) {
                JOptionPane.showMessageDialog(staffDialog, "Password must be 12 digits", "Error",
                        JOptionPane.ERROR_MESSAGE);
                isValid = false;
            }

            if (isValid) {
                try {
                    // --- MODIFICATION START ---
                    // ID Generation for Staff
                    List<User> staffList = loadStaffFromFile();
                    int nextStaffId = staffList.stream().mapToInt(s -> s.id).max().orElse(0) + 1;
                    // --- MODIFICATION END ---

                    User staff = new User();
                    staff.id = nextStaffId; // Assign the new unique ID
                    staff.name = name;
                    staff.age = Integer.parseInt(ageField.getText());
                    staff.birthday = birthdayField.getText();
                    staff.civilStatus = (String) civilStatusCombo.getSelectedItem();
                    staff.bloodType = bloodType;
                    staff.contactNumber = contactNumber;
                    staff.profession = (String) professionCombo.getSelectedItem();
                    staff.username = usernameField.getText().trim();
                    staff.password = new String(passwordField.getPassword());
                    staff.imagePath = selectedImagePath[0];

                    saveStaffToFile(staff);
                    JOptionPane.showMessageDialog(staffDialog,
                            "Staff registered successfully!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    staffDialog.dispose();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(staffDialog,
                            "Error saving staff data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        cancelButton.addActionListener(e -> staffDialog.dispose());

        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        staffDialog.add(mainPanel);
        staffDialog.pack();
        staffDialog.setLocationRelativeTo(mainFrame);
        staffDialog.setVisible(true);
    }

    private static boolean isUsernameExistsInStaff(String username) {
        try {
            Path staffPath = Paths.get(STAFF_DATABASE);
            if (!Files.exists(staffPath))
                return false;

            return Files.lines(staffPath)
                    .anyMatch(line -> {
                        try {
                            User staff = User.fromString(line);
                            return staff != null && staff.username.equalsIgnoreCase(username);
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Error checking username: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return true; // Prevent registration on error
        }
    }

    private static void saveStaffToFile(User staff) throws IOException {
        Path path = Paths.get(STAFF_DATABASE);

        // Use the User's own serialization method to ensure consistency
        String staffRecord = staff.toStringForFile();

        Files.write(
                path,
                (staffRecord + System.lineSeparator()).getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static void editMedicalStaff() {
        List<User> staffList = loadStaffFromFile();

        if (staffList.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No medical staff found to edit.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        User selectedStaff = (User) JOptionPane.showInputDialog(
                mainFrame,
                "Select staff member to edit:",
                "Edit Medical Staff",
                JOptionPane.PLAIN_MESSAGE,
                null,
                staffList.toArray(),
                staffList.get(0));

        if (selectedStaff == null) {
            return; // User cancelled
        }

        // Create the editing dialog
        JDialog editDialog = new JDialog(mainFrame, "Edit Staff: " + selectedStaff.name, true);
        editDialog.setLayout(new BorderLayout(10, 10));
        editDialog.setSize(600, 500); // Adjust size as needed
        editDialog.setLocationRelativeTo(mainFrame);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // --- Profession ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Profession:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> professionCombo = new JComboBox<>(MEDICAL_PROFESSIONS);
        professionCombo.setSelectedItem(selectedStaff.profession); // Pre-fill
        formPanel.add(professionCombo, gbc);

        // --- Contact Number ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Contact Number (XXXX-XXX-XXXX):"), gbc);
        gbc.gridx = 1;
        JTextField contactNumberField = new JTextField(selectedStaff.contactNumber, 15); // Pre-fill
        formPanel.add(contactNumberField, gbc);
        gbc.gridx = 2;
        JLabel contactNumberError = new JLabel();
        contactNumberError.setForeground(Color.RED);
        formPanel.add(contactNumberError, gbc);

        // --- Civil Status ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JLabel("Civil Status:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> civilStatusCombo = new JComboBox<>(
                new String[] { "Single", "Married", "Widowed", "Divorced" }); // Added more options
        civilStatusCombo.setSelectedItem(selectedStaff.civilStatus); // Pre-fill
        formPanel.add(civilStatusCombo, gbc);
        gbc.gridx = 2;
        JLabel civilStatusError = new JLabel();
        civilStatusError.setForeground(Color.RED);
        formPanel.add(civilStatusError, gbc);

        // --- Profile Picture ---
        JPanel imagePanel = new JPanel(new BorderLayout(5, 5));
        imagePanel.setBorder(BorderFactory.createTitledBorder("Profile Picture"));
        JLabel imagePreviewLabel = new JLabel();
        imagePreviewLabel.setPreferredSize(new Dimension(150, 150));
        imagePreviewLabel.setHorizontalAlignment(JLabel.CENTER);
        imagePreviewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        final String[] newImagePath = { selectedStaff.imagePath }; // Store current path, potentially update

        if (selectedStaff.imagePath != null && !selectedStaff.imagePath.isEmpty()) {
            try {
                File imgFile = new File(selectedStaff.imagePath);
                if (imgFile.exists()) {
                    ImageIcon icon = new ImageIcon(selectedStaff.imagePath);
                    Image img = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                    imagePreviewLabel.setIcon(new ImageIcon(img));
                } else {
                    imagePreviewLabel.setText("Image not found");
                }
            } catch (Exception ex) {
                imagePreviewLabel.setText("Error loading image");
            }
        } else {
            imagePreviewLabel.setText("No image");
        }
        imagePanel.add(imagePreviewLabel, BorderLayout.CENTER);

        JButton changePictureButton = new JButton("Change Picture");
        changePictureButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select New Profile Picture");
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.addChoosableFileFilter(
                    new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes()));

            if (fileChooser.showOpenDialog(editDialog) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    BufferedImage originalImage = ImageIO.read(selectedFile);
                    Image scaledImage = originalImage.getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                    imagePreviewLabel.setIcon(new ImageIcon(scaledImage));
                    newImagePath[0] = selectedFile.getAbsolutePath(); // Update path
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(editDialog, "Error loading new image: " + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        imagePanel.add(changePictureButton, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        formPanel.add(imagePanel, gbc);

        // --- Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save Changes");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            // Reset errors
            contactNumberError.setText("");
            civilStatusError.setText("");
            boolean isValid = true;

            // Validate Contact Number
            String contactNumber = contactNumberField.getText().trim();
            if (!contactNumber.matches("^\\d{4}-\\d{3}-\\d{4}$")) {
                contactNumberError.setText("Format: XXXX-XXX-XXXX");
                isValid = false;
            }

            // Validate Civil Status (optional, as it's a combo box, but good practice)
            String civilStatus = (String) civilStatusCombo.getSelectedItem();
            if (civilStatus == null || civilStatus.trim().isEmpty()) {
                civilStatusError.setText("Required"); // Should not happen with JComboBox unless editable and cleared
                isValid = false;
            }

            String profession = (String) professionCombo.getSelectedItem();
            // Add validation for profession if "Other" is selected and an input field
            // appears

            if (isValid) {
                // Update the selectedStaff object's details
                selectedStaff.profession = profession;
                selectedStaff.contactNumber = contactNumber;
                selectedStaff.civilStatus = civilStatus;
                selectedStaff.imagePath = newImagePath[0]; // Use the potentially updated image path

                try {
                    // Find the index of the staff member in the list to update it directly
                    // This is important if User objects are not directly modified from the list
                    int staffIndex = -1;
                    for (int i = 0; i < staffList.size(); i++) {
                        // Assuming User objects have a unique identifier like 'username' or 'id'
                        // If User.equals() is properly overridden, you can use
                        // staffList.indexOf(selectedStaff)
                        // For this example, let's assume username is unique and was not changed.
                        if (staffList.get(i).username.equals(selectedStaff.username)) {
                            staffIndex = i;
                            break;
                        }
                    }

                    if (staffIndex != -1) {
                        staffList.set(staffIndex, selectedStaff); // Update the object in the list
                    } else {
                        // Fallback or error: if staff member was identified by reference and already
                        // updated
                        // Or if identification failed. For now, we assume selectedStaff is the
                        // reference from the list.
                    }

                    saveAllStaffToFile(staffList); // Save the entire updated list
                    JOptionPane.showMessageDialog(editDialog, "Staff information updated successfully!", "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    editDialog.dispose();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(editDialog, "Error saving staff data: " + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        cancelButton.addActionListener(e -> editDialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        editDialog.add(formPanel, BorderLayout.CENTER);
        editDialog.add(buttonPanel, BorderLayout.SOUTH);
        editDialog.setVisible(true);
    }

    private static void saveAllStaffToFile(List<User> staffList) throws IOException {
        Path path = Paths.get(STAFF_DATABASE);
        List<String> lines = new ArrayList<>();
        for (User staff : staffList) {
            lines.add(staff.toStringForFile()); // Uses your existing User.toStringForFile()
        }
        // Write all lines, overwriting the file if it exists, or creating it if it
        // doesn't.
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void removeMedicalStaff() {
        // Let admin choose between Staff or Doctors
        String[] options = { "Staff Member", "Doctor" };
        String choice = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select type to remove:",
                "Remove Medical Staff/Doctor",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == null)
            return; // User cancelled

        // Load the appropriate list
        List<User> targetList = choice.equals("Staff Member") ? loadStaffFromFile() : loadDoctorsFromFile();

        if (targetList.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame,
                    "No " + choice.toLowerCase() + "s found to remove.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create display strings for the selection dialog
        String[] displayOptions = targetList.stream()
                .map(u -> u.name + " (" + u.profession + ")")
                .toArray(String[]::new);

        // Let admin select specific person to remove
        String selected = (String) JOptionPane.showInputDialog(
                mainFrame,
                "Select " + choice.toLowerCase() + " to remove:",
                "Remove " + choice,
                JOptionPane.PLAIN_MESSAGE,
                null,
                displayOptions,
                displayOptions[0]);

        if (selected != null) {
            int index = Arrays.asList(displayOptions).indexOf(selected);
            User userToRemove = targetList.get(index);

            // Confirm removal
            int confirm = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Are you sure you want to remove:\n" +
                            userToRemove.name + "\n" +
                            "Profession: " + userToRemove.profession,
                    "Confirm Removal",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    // Remove from appropriate file
                    Path filePath = choice.equals("Staff Member") ? Paths.get(STAFF_DATABASE)
                            : Paths.get(DOCTORS_DATABASE);

                    List<String> lines = Files.readAllLines(filePath);
                    List<String> updatedLines = lines.stream()
                            .filter(line -> !line.contains(userToRemove.username))
                            .collect(Collectors.toList());

                    Files.write(filePath, updatedLines);

                    JOptionPane.showMessageDialog(mainFrame,
                            "Successfully removed " + choice.toLowerCase() + ": " + userToRemove.name,
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Error removing " + choice.toLowerCase() + ":\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private static void showUserMedication() {
        if (currentUser == null)
            return;

        JDialog dialog = new JDialog(mainFrame, "Your Medications", true);
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabs = new JTabbedPane();

        // Current Medications
        JTextArea currentText = new JTextArea();
        currentText.setEditable(false);
        List<String[]> currentMeds = loadMedicationsForUser(currentUser.username, false);
        if (currentMeds.isEmpty()) {
            currentText.setText("No current medications.");
        } else {
            for (String[] med : currentMeds) {
                currentText.append("Medication: " + med[1] + "\n");
                currentText.append("Dosage: " + med[2] + "\n");
                currentText.append("Instructions: " + med[3] + "\n");
                currentText.append("Period: " + med[4] + " to " + med[5] + "\n");
                currentText.append("--------\n");
            }
        }

        // Past Medications
        JTextArea pastText = new JTextArea();
        pastText.setEditable(false);
        List<String[]> pastMeds = loadMedicationsForUser(currentUser.username, true);
        if (pastMeds.isEmpty()) {
            pastText.setText("No past medications.");
        } else {
            for (String[] med : pastMeds) {
                pastText.append("Medication: " + med[1] + "\n");
                pastText.append("Dosage: " + med[2] + "\n");
                pastText.append("Instructions: " + med[3] + "\n");
                pastText.append("Period: " + med[4] + " to " + med[5] + "\n");
                pastText.append("--------\n");
            }
        }

        tabs.addTab("Current Medications", new JScrollPane(currentText));
        tabs.addTab("Past Medications", new JScrollPane(pastText));

        dialog.add(tabs);
        dialog.setVisible(true);
    }

    private static List<String[]> loadMedicationsForUser(String username, boolean pastOnly) {
        List<String[]> result = new ArrayList<>();
        try {
            File file = new File(MEDICATION_DATABASE);
            if (!file.exists()) {
                System.err.println("Medication file not found: " + MEDICATION_DATABASE);
                return result;
            }

            List<String> lines = Files.readAllLines(Paths.get(MEDICATION_DATABASE)); // Read all lines at once
            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm");
            Date now = new Date();

            for (String line : lines) {
                // --- MODIFICATION START: Expect 7 parts ---
                String[] parts = line.split(";", -1); // Use -1 to keep trailing empty strings
                if (parts.length >= 7 && parts[0].equals(username)) { // Check for at least 7 parts
                    // --- MODIFICATION END ---
                    try {
                        Date endDate = format.parse(parts[5]); // endDate is at index 5
                        boolean isPast = endDate.before(now);

                        if ((pastOnly && isPast) || (!pastOnly && !isPast)) {
                            result.add(parts); // Add the whole array, including the doctor's name at parts[6]
                        }
                    } catch (ParseException pe) {
                        System.err
                                .println("Could not parse date for medication line: " + line + " - " + pe.getMessage());
                    }
                }
            }
        } catch (Exception e) { // Catch generic Exception for other IO issues
            JOptionPane.showMessageDialog(mainFrame, "Error loading medications: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        return result;
    }

    private static void loadMedicationData(JTextArea currentArea, JTextArea pastArea, String username) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

        try (BufferedReader br = new BufferedReader(new FileReader(MEDICATION_DATABASE))) {
            String line;
            StringBuilder currentMed = new StringBuilder();
            StringBuilder pastMed = new StringBuilder();

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length == 6) {
                    String user = parts[0].trim();
                    String medName = parts[1].trim();
                    String dosage = parts[2].trim();
                    String instructions = parts[3].trim();
                    String startDate = parts[4].trim();
                    String endDate = parts[5].trim();

                    if (user.equalsIgnoreCase(username)) {
                        String medDetails = "Medication: " + medName + "\nDosage: " + dosage +
                                "\nInstructions: " + instructions +
                                "\nStart: " + startDate + "\nEnd: " + endDate + "\n\n";

                        LocalDateTime end = LocalDateTime.parse(endDate, formatter);
                        if (LocalDateTime.now().isBefore(end)) {
                            currentMed.append(medDetails);
                        } else {
                            pastMed.append(medDetails);
                        }
                    }
                }
            }

            currentArea.setText(currentMed.length() > 0 ? currentMed.toString() : "No current medication.");
            pastArea.setText(pastMed.length() > 0 ? pastMed.toString() : "No past medication.");

        } catch (Exception e) {
            currentArea.setText("Error loading current medications.");
            pastArea.setText("Error loading past medications.");
            e.printStackTrace();
        }
    }

    // Change the signature to include doctorName
    private static void saveMedicationToFile(String username, String name, String dose, String instructions,
            String start, String end, String doctorName) { // Added doctorName
        try {
            Path path = Paths.get(MEDICATION_DATABASE);
            Files.createDirectories(path.getParent());

            // --- MODIFICATION START: Add doctorName to the line ---
            String line = String.join(";", username, name, dose, instructions, start, end, doctorName);
            // --- MODIFICATION END ---

            try (FileWriter fw = new FileWriter(MEDICATION_DATABASE, true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw)) {
                out.println(line);
                out.flush();
            }
            System.out.println("Saved medication to: " + path.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(mainFrame,
                    "Failed to save medication. Error:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void showUserMedicalHistory() {
        if (currentUser == null)
            return;

        JDialog dialog = new JDialog(mainFrame, "Medical History", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(mainFrame);

        if (currentUser.medicalHistory.isEmpty()) {
            dialog.add(new JLabel("No medical history available.", JLabel.CENTER), BorderLayout.CENTER);
            dialog.setVisible(true);
            return;
        }

        JTabbedPane tabbedPane = new JTabbedPane();

        for (String[] history : currentUser.medicalHistory) {
            JPanel historyPanel = new JPanel(new BorderLayout(10, 10));
            historyPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Left panel - history text
            JTextArea historyText = new JTextArea();
            historyText.setEditable(false);
            historyText.setFont(new Font("Arial", Font.PLAIN, 14));
            historyText.setText(history[0]);

            // Right panel - verification image if exists
            if (history.length > 2 && !history[2].isEmpty()) {
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
                splitPane.setDividerLocation(400);

                splitPane.setLeftComponent(new JScrollPane(historyText));

                JPanel imagePanel = new JPanel(new BorderLayout());
                imagePanel.setBorder(BorderFactory.createTitledBorder("Verification Image"));
                try {
                    File imgFile = new File(history[2]);
                    if (imgFile.exists()) {
                        ImageIcon icon = new ImageIcon(history[2]);
                        Image image = icon.getImage().getScaledInstance(300, 300, Image.SCALE_SMOOTH);
                        imagePanel.add(new JLabel(new ImageIcon(image)), BorderLayout.CENTER);
                    } else {
                        imagePanel.add(new JLabel("Image not found"), BorderLayout.CENTER);
                    }
                } catch (Exception e) {
                    imagePanel.add(new JLabel("Error loading image"), BorderLayout.CENTER);
                }

                splitPane.setRightComponent(imagePanel);
                historyPanel.add(splitPane, BorderLayout.CENTER);
            } else {
                historyPanel.add(new JScrollPane(historyText), BorderLayout.CENTER);
            }

            // Add date label at bottom
            JLabel dateLabel = new JLabel("Date: " + history[1], JLabel.RIGHT);
            historyPanel.add(dateLabel, BorderLayout.SOUTH);

            tabbedPane.addTab(history[1], historyPanel);
        }

        dialog.add(tabbedPane, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private static void showManageAccountDialog() {
        if (currentUser == null)
            return;

        String[] options = { "Update Username", "Update Password", "Update Information", "Cancel" };
        int choice = JOptionPane.showOptionDialog(mainFrame,
                "What would you like to update?",
                "Manage Account",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) {
            updateUsername();
        } else if (choice == 1) {
            updatePassword();
        } else if (choice == 2) {
            showUserUpdateDialog(mainFrame); // ← move it here
        }
    }

    private static void updateUsername() {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        JTextField newUsernameField = new JTextField(15);
        JTextField confirmUsernameField = new JTextField(15);

        panel.add(new JLabel("New Username:"));
        panel.add(newUsernameField);
        panel.add(new JLabel("Confirm Username:"));
        panel.add(confirmUsernameField);

        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                panel,
                "Update Username",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newUsername = newUsernameField.getText().trim();
            String confirmUsername = confirmUsernameField.getText().trim();

            if (!newUsername.equals(confirmUsername)) {
                JOptionPane.showMessageDialog(mainFrame, "Usernames don't match!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (newUsername.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Username cannot be empty!", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Check if username already exists
            for (User user : users) {
                if (user.username.equals(newUsername)) {
                    JOptionPane.showMessageDialog(mainFrame, "Username already exists!", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            currentUser.username = newUsername;
            updateDatabaseFile();
            JOptionPane.showMessageDialog(mainFrame, "Username updated successfully!");
        }
    }

    private static void updatePassword() {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        JPasswordField newPasswordField = new JPasswordField(15);
        JPasswordField confirmPasswordField = new JPasswordField(15);

        panel.add(new JLabel("New Password (12 digits):"));
        panel.add(newPasswordField);
        panel.add(new JLabel("Confirm Password:"));
        panel.add(confirmPasswordField);

        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                panel,
                "Update Password",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newPassword = new String(newPasswordField.getPassword()).trim();
            String confirmPassword = new String(confirmPasswordField.getPassword()).trim();

            if (!newPassword.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(mainFrame, "Passwords don't match!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (newPassword.length() != 12) {
                JOptionPane.showMessageDialog(mainFrame, "Password must be 12 digits!", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            currentUser.password = newPassword;
            updateDatabaseFile();
            JOptionPane.showMessageDialog(mainFrame, "Password updated successfully!");
        }
    }

    private static void updateDatabaseFile() {
        try {
            // Directly write to the original file
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(USER_DATABASE)))) {
                for (User user : users) {
                    out.println(user.toStringForFile());
                }
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error updating user data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static class User {
        String name, birthday, civilStatus, bloodType;
        String contactNumber, emergencyContactNumber;
        String username, password;
        int age;
        String imagePath;
        int id;
        String dateAdded;
        List<String> allergies = new ArrayList<>();
        List<String> medicalConditions = new ArrayList<>();
        List<String[]> medications = new ArrayList<>();
        List<String[]> medicalHistory = new ArrayList<>();
        String profession; // Add this line
        String bloodPressure = "";
        String heartRate = "";
        String temperature = "";
        String oxygenLevel = "";
        String referredDoctor;
        String referralStatus;
        String referralDate;

        @Override
        public String toString() {
            return name + " (" + username + ")";
        }

        public String getMedicalHistoryAsString() {
            if (medicalHistory.isEmpty()) {
                return "No medical history available.";
            }

            StringBuilder sb = new StringBuilder();
            for (String[] history : medicalHistory) {
                sb.append("• ").append(history[0]).append("\n");
                sb.append("  Occurred: ").append(history[1]).append("\n\n");
            }
            return sb.toString();
        }

        public String toStringForFile() {
            // ... existing code ...
            return String.join(",",
                    String.valueOf(id),
                    name,
                    String.valueOf(age),
                    birthday,
                    civilStatus,
                    bloodType,
                    contactNumber,
                    emergencyContactNumber,
                    username,
                    password,
                    imagePath != null ? imagePath : "",
                    String.join(";", allergies),
                    String.join(";", medicalConditions),
                    serializeStringList(medications),
                    serializeMedicalHistory(), // Updated method
                    dateAdded != null ? dateAdded : "",
                    profession != null ? profession : "",
                    bloodPressure,
                    heartRate,
                    temperature,
                    oxygenLevel);
        }
        // Inside public class User { ... }

        // Modify how medical history is serialized in toStringForFile()
        private String serializeMedicalHistory() {
            if (medicalHistory.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String[] history : medicalHistory) {
                if (history != null && history.length > 0) {
                    String[] escapedItems = new String[history.length];
                    for (int i = 0; i < history.length; i++) {
                        // Ensure doctor name (history[3]) is handled even if empty
                        String itemToEscape = (history[i] != null) ? history[i] : "";
                        escapedItems[i] = itemToEscape.replace("|", "\\|").replace(";", "\\;");
                    }
                    sb.append(String.join("|", escapedItems)).append(";");
                }
            }
            // Remove trailing semicolon if present
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ';') {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        }

        // Modify how medical history is deserialized in fromString()
        public static User fromString(String data) {
            try {
                String[] parts = data.split(",", -1); // -1 keeps empty values
                User user = new User();
                // ... (your existing parsing for id, name, age, etc.) ...
                user.id = Integer.parseInt(parts[0]);
                user.name = parts[1];
                user.age = Integer.parseInt(parts[2]);
                user.birthday = parts[3];
                user.civilStatus = parts[4];
                user.bloodType = parts[5];
                user.contactNumber = parts[6];
                user.emergencyContactNumber = parts[7];
                user.username = parts[8];
                user.password = parts[9];
                user.imagePath = parts[10].isEmpty() ? null : parts[10];

                if (!parts[11].isEmpty()) { // Allergies
                    user.allergies.addAll(Arrays.asList(parts[11].split("(?<!\\\\);"))); // Handle escaped semicolons if
                                                                                         // any
                }
                if (!parts[12].isEmpty()) { // Medical Conditions
                    user.medicalConditions.addAll(Arrays.asList(parts[12].split("(?<!\\\\);")));
                }

                // Medications (index 13) - No change needed here for prescribed by, handled in
                // medication.txt
                if (parts.length > 13 && !parts[13].isEmpty()) {
                    // ... your existing medication parsing ...
                }

                // Medical History (index 14)
                if (parts.length > 14 && !parts[14].isEmpty()) {
                    String[] histEntries = parts[14].split("(?<!\\\\);"); // Split by non-escaped semicolon
                    for (String histEntry : histEntries) {
                        if (!histEntry.isEmpty()) {
                            String[] histParts = histEntry.split("(?<!\\\\)\\|"); // Split by non-escaped pipe
                            String[] fullHistParts = new String[4]; // Now expecting 4 parts
                            fullHistParts[0] = (histParts.length > 0)
                                    ? histParts[0].replace("\\|", "|").replace("\\;", ";")
                                    : ""; // Text
                            fullHistParts[1] = (histParts.length > 1)
                                    ? histParts[1].replace("\\|", "|").replace("\\;", ";")
                                    : ""; // Date
                            fullHistParts[2] = (histParts.length > 2)
                                    ? histParts[2].replace("\\|", "|").replace("\\;", ";")
                                    : ""; // Image Path
                            fullHistParts[3] = (histParts.length > 3)
                                    ? histParts[3].replace("\\|", "|").replace("\\;", ";")
                                    : ""; // Doctor Name
                            user.medicalHistory.add(fullHistParts);
                        }
                    }
                }
                // ... (rest of your parsing for dateAdded, profession, etc.) ...
                user.dateAdded = parts.length > 15 ? parts[15] : "Unknown";
                user.profession = parts.length > 16 ? parts[16] : "";
                // ... (bloodPressure, heartRate, etc.)

                return user;
            } catch (Exception e) {
                System.err.println("Error parsing user data: " + data);
                e.printStackTrace();
                return null;
            }
        }

        private static String serializeStringList(List<String[]> list) {
            if (list.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (String[] item : list) {
                if (item != null && item.length > 0) {
                    // Escape any existing pipes in the data
                    String[] escapedItems = new String[item.length];
                    for (int i = 0; i < item.length; i++) {
                        escapedItems[i] = item[i] != null ? item[i].replace("|", "\\|") : "";
                    }
                    sb.append(String.join("|", escapedItems)).append(";");
                }
            }
            return sb.toString();
        }
    }

    // Update createStaffMenuPanel()
    private static void createStaffMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Staff Menu", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(titleLabel, gbc);

        JButton scanQRButton = new JButton("1. Scan QR Code");
        scanQRButton.addActionListener(e -> startScanner());
        panel.add(scanQRButton, gbc);

        JButton addDetailsButton = new JButton("2. Add User Details");
        addDetailsButton.addActionListener(e -> showAddUserDetailsDialog());
        panel.add(addDetailsButton, gbc);

        JButton addUserAccountButton = new JButton("3. Add User Account");
        addUserAccountButton.addActionListener(e -> cardLayout.show(cardPanel, "AddUserAccount"));
        panel.add(addUserAccountButton, gbc);

        JButton masterlistButton = new JButton("4. View User Masterlist");
        masterlistButton.addActionListener(e -> showMasterlistDialog(false));
        panel.add(masterlistButton, gbc);

        // New Manage Account Button
        JButton manageAccountButton = new JButton("5. Manage Account");
        manageAccountButton.addActionListener(e -> showStaffManageAccountDialog());
        panel.add(manageAccountButton, gbc);

        // Updated Exit Button
        JButton exitButton = new JButton("6. Exit");
        exitButton.addActionListener(e -> {
            currentUser = null;
            loginUserField.setText("");
            loginPassField.setText("");
            cardLayout.show(cardPanel, "MainMenu");
        });
        panel.add(exitButton, gbc);

        cardPanel.add(panel, "StaffMenu");
    }

    private static void showAddUserDetailsDialog() {
        JDialog searchDialog = new JDialog(mainFrame, "Select User", true);
        searchDialog.setLayout(new BorderLayout());
        searchDialog.setSize(800, 500);
        searchDialog.setLocationRelativeTo(mainFrame);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);

        // Left Panel - Search
        JPanel leftPanel = new JPanel(new BorderLayout());
        JTextField searchField = new JTextField();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(listModel);

        // Right Panel - User Count
        JTextArea countArea = new JTextArea();
        countArea.setEditable(false);
        updateCountDisplay(countArea);

        // Search Panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("Search by ID or Name:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Selection Button Panel
        JPanel buttonPanel = new JPanel();
        JButton selectButton = new JButton("Select User");
        selectButton.setEnabled(false);

        // List Selection Listener
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectButton.setEnabled(userList.getSelectedValue() != null);
            }
        });

        // Search Field Listener
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                update();
            }

            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            private void update() {
                updateUserList(listModel, searchField.getText());
                updateCountDisplay(countArea);
            }
        });

        // Select Button Action
        selectButton.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected != null) {
                int userId = Integer.parseInt(selected.split(" - ")[0]);
                User selectedUser = users.stream()
                        .filter(u -> u.id == userId)
                        .findFirst()
                        .orElse(null);

                if (selectedUser != null) {
                    searchDialog.dispose();
                    showUserDetailsTabs(selectedUser);
                }
            }
        });

        // Add components
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        buttonPanel.add(selectButton);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(new JScrollPane(countArea));

        searchDialog.add(splitPane);
        searchDialog.setVisible(true);
    }

    private static void updateCountDisplay(JTextArea area) {
        area.setText("Total Users: " + users.size() + "\n\n");
        for (User u : users) {
            area.append(String.format("%03d - %s\n", u.id, u.name));
        }
    }

    private static void updateUserList(DefaultListModel<String> model, String filter) {
        model.clear();
        users.stream()
                .filter(u -> String.format("%03d", u.id).contains(filter) ||
                        u.name.toLowerCase().contains(filter.toLowerCase()))
                .forEach(u -> model.addElement(String.format("%03d - %s", u.id, u.name)));
    }

    private static void showUserDetailsTabs(User user) {
        JDialog detailsDialog = new JDialog(mainFrame, "User Details - " + user.name, true);
        detailsDialog.setSize(900, 600);
        detailsDialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Create profile tab and extract display components
        JPanel profileTab = createProfileTab(user);
        JTextArea profileAllergiesArea = extractProfileComponent(profileTab, 7); // Allergies text area
        JTextArea profileConditionsArea = extractProfileComponent(profileTab, 8); // Conditions text area

        // Create medical info tab with profile component references
        JPanel medicalInfoTab = createMedicalInfoTab(user, profileAllergiesArea, profileConditionsArea);

        tabbedPane.addTab("Profile", profileTab);
        tabbedPane.addTab("Medical Information", medicalInfoTab);
        tabbedPane.addTab("Vital Assessment", createVitalsPanel(user));

        detailsDialog.add(tabbedPane);
        detailsDialog.setVisible(true);
    }

    private static JTextArea extractProfileComponent(JPanel profileTab, int lineIndex) {
        try {
            // Navigate through the profile tab components hierarchy
            JScrollPane scrollPane = (JScrollPane) profileTab.getComponent(0);
            JViewport viewport = scrollPane.getViewport();
            JTextArea profileTextArea = (JTextArea) viewport.getView();

            // Split the profile text into lines
            String[] lines = profileTextArea.getText().split("\n");
            if (lines.length > lineIndex) {
                // Create a dedicated text area for the specific profile field
                JTextArea fieldArea = new JTextArea();
                fieldArea.setText(lines[lineIndex].split(": ")[1].trim());
                return fieldArea;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JTextArea();
    }

    private static JPanel createProfileTab(User user) {
        JPanel profilePanel = new JPanel(new BorderLayout(10, 10));
        profilePanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Main content panel
        JPanel contentPanel = new JPanel(new BorderLayout(20, 10));

        // Image panel (right side)
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setPreferredSize(new Dimension(200, 200));
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        if (user.imagePath != null && !user.imagePath.isEmpty()) {
            try {
                ImageIcon icon = new ImageIcon(user.imagePath);
                Image image = icon.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(image));
            } catch (Exception e) {
                imageLabel.setText("Image not available");
            }
        } else {
            imageLabel.setText("No profile image");
        }
        imagePanel.add(imageLabel, BorderLayout.CENTER);

        // Info panel (left side)
        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 5, 10));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        addProfileField(infoPanel, "Name:", user.name);
        addProfileField(infoPanel, "Age:", String.valueOf(user.age));
        addProfileField(infoPanel, "Birthday:", user.birthday);
        addProfileField(infoPanel, "Civil Status:", user.civilStatus);
        addProfileField(infoPanel, "Blood Type:", user.bloodType);
        addProfileField(infoPanel, "Contact Number:", user.contactNumber);
        addProfileField(infoPanel, "Emergency Contact:", user.emergencyContactNumber);
        addProfileField(infoPanel, "Allergies:", String.join(", ", user.allergies));
        addProfileField(infoPanel, "Medical Conditions:", String.join(", ", user.medicalConditions));

        contentPanel.add(new JScrollPane(infoPanel), BorderLayout.CENTER);
        contentPanel.add(imagePanel, BorderLayout.EAST);

        profilePanel.add(contentPanel, BorderLayout.CENTER);
        return profilePanel;
    }

    private static void addProfileField(JPanel panel, String label, String value) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD));

        JLabel valueComponent = new JLabel(value.isEmpty() ? "N/A" : value);

        panel.add(labelComponent);
        panel.add(valueComponent);
    }

    private static JPanel createMedicalInfoTab(User user, JTextArea profileAllergies, JTextArea profileConditions) {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create split panel for allergies/conditions
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);

        // Allergies Panel
        JPanel allergiesPanel = createCategoryPanel(
                user,
                "Allergies",
                COMMON_ALLERGIES,
                user.allergies,
                profileAllergies);

        // Conditions Panel
        JPanel conditionsPanel = createCategoryPanel(
                user,
                "Medical Conditions",
                COMMON_MEDICAL_CONDITIONS,
                user.medicalConditions,
                profileConditions);

        splitPane.setLeftComponent(allergiesPanel);
        splitPane.setRightComponent(conditionsPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        return mainPanel;
    }

    private static void updateProfileDisplay() {
        if (currentUser != null) {
            // Reload the user from the updated list
            currentUser = users.stream()
                    .filter(u -> u.username.equals(currentUser.username))
                    .findFirst()
                    .orElse(null);
            // Refresh the profile UI
            showUserProfile();
        }
    }

    private static JPanel createCategoryPanel(User user, String title, String[] commonItems,
            List<String> items, JTextArea profileField) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));

        // List display
        DefaultListModel<String> listModel = new DefaultListModel<>();
        items.forEach(listModel::addElement);
        JList<String> itemList = new JList<>(listModel);

        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton removeButton = new JButton("Remove");

        // Add button action
        addButton.addActionListener(e -> {
            Object selection = JOptionPane.showInputDialog(
                    panel,
                    "Select or enter " + title.toLowerCase() + ":",
                    "Add " + title,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    commonItems,
                    commonItems[0]);

            if (selection != null) {
                String value = selection.toString();
                if (value.startsWith("Other (specify)")) {
                    value = JOptionPane.showInputDialog("Enter custom " + title.substring(0, title.length() - 1) + ":");
                }

                if (value != null && !value.trim().isEmpty()) {
                    if (title.equals("Allergies")) {
                        String severity = (String) JOptionPane.showInputDialog(
                                panel,
                                "Select severity:",
                                "Severity",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                new String[] { "mild", "severe" },
                                "mild");
                        value += " (" + severity + ")";
                    }

                    items.add(value);
                    listModel.addElement(value);
                    saveUserToFile(user);
                    updateProfileDisplay();
                    updateProfileField(profileField, items);
                }
            }
        });

        // Edit button action
        editButton.addActionListener(e -> {
            String selected = itemList.getSelectedValue();
            if (selected != null) {
                String newValue = (String) JOptionPane.showInputDialog(
                        panel,
                        "Edit entry:",
                        "Edit " + title,
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        selected.replaceAll("\\(.*\\)", "").trim());

                if (newValue != null && !newValue.isEmpty()) {
                    int index = itemList.getSelectedIndex();
                    if (title.equals("Allergies")) {
                        String severity = (String) JOptionPane.showInputDialog(
                                panel,
                                "Select severity:",
                                "Severity",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                new String[] { "mild", "severe" },
                                selected.contains("severe") ? "severe" : "mild");
                        newValue += " (" + severity + ")";
                    }

                    items.set(index, newValue);
                    listModel.set(index, newValue);
                    saveUserToFile(user);
                    updateProfileField(profileField, items);
                }
            }
        });

        // Remove button action
        removeButton.addActionListener(e -> {
            String selected = itemList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(
                        panel,
                        "Remove " + selected + "?",
                        "Confirm Removal",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    int index = itemList.getSelectedIndex();
                    items.remove(index);
                    listModel.remove(index);
                    saveUserToFile(user);
                    updateProfileField(profileField, items);
                }
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        panel.add(new JScrollPane(itemList), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static void updateProfileField(JTextArea profileField, List<String> items) {
        SwingUtilities.invokeLater(() -> {
            // Update specific profile field
            profileField.setText(String.join(", ", items));

            // Update full profile text
            Container parent = profileField.getParent();
            while (!(parent instanceof JDialog) && parent != null) {
                parent = parent.getParent();
            }

            if (parent instanceof JDialog) {
                Component[] tabs = ((JTabbedPane) ((JDialog) parent).getContentPane().getComponent(0)).getComponents();
                JScrollPane profileScroll = (JScrollPane) tabs[0];
                JTextArea fullProfile = (JTextArea) profileScroll.getViewport().getView();

                String[] lines = fullProfile.getText().split("\n");
                if (profileField.getName().contains("Allergies")) {
                    lines[7] = "Allergies: " + String.join(", ", items);
                } else {
                    lines[8] = "Medical Conditions: " + String.join(", ", items);
                }

                fullProfile.setText(String.join("\n", lines));
            }
        });
    }

    private static void updateListModel(DefaultListModel<String> model, List<String> items) {
        model.clear();
        for (String item : items) {
            model.addElement(item);
        }
    }

    private static void showManagementDialog(User user, boolean isAllergies, JTextArea mainDisplayArea) {
        JDialog dialog = new JDialog(mainFrame, isAllergies ? "Manage Allergies" : "Manage Medical Conditions", true);
        dialog.setLayout(new BorderLayout());
        dialog.setPreferredSize(new Dimension(400, 300));

        JPanel contentPanel = new JPanel(new BorderLayout());
        JTextArea currentItems = new JTextArea();
        currentItems.setEditable(false);
        currentItems.setFont(new Font("Arial", Font.PLAIN, 14));

        // Initialize current items
        if (isAllergies) {
            currentItems.setText(String.join("\n", user.allergies));
        } else {
            currentItems.setText(String.join("\n", user.medicalConditions));
        }

        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton removeButton = new JButton("Remove");

        // Add Button Logic
        addButton.addActionListener(e -> {
            String newItem = JOptionPane.showInputDialog(dialog,
                    "Enter new " + (isAllergies ? "allergy" : "condition") + ":",
                    "Add Item",
                    JOptionPane.PLAIN_MESSAGE);

            if (newItem != null && !newItem.trim().isEmpty()) {
                if (isAllergies) {
                    user.allergies.add(newItem.trim());
                } else {
                    user.medicalConditions.add(newItem.trim());
                }
                updateDisplay(currentItems, user, isAllergies, mainDisplayArea);
            }
        });

        // Edit Button Logic
        editButton.addActionListener(e -> {
            String selected = currentItems.getSelectedText();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Please select an item to edit",
                        "No Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String newValue = JOptionPane.showInputDialog(dialog,
                    "Edit item:",
                    selected);

            if (newValue != null && !newValue.trim().isEmpty()) {
                List<String> targetList = isAllergies ? user.allergies : user.medicalConditions;
                int index = targetList.indexOf(selected);
                if (index != -1) {
                    targetList.set(index, newValue.trim());
                    updateDisplay(currentItems, user, isAllergies, mainDisplayArea);
                }
            }
        });

        // Remove Button Logic
        removeButton.addActionListener(e -> {
            String selected = currentItems.getSelectedText();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Please select an item to remove",
                        "No Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Are you sure you want to remove '" + selected + "'?",
                    "Confirm Removal",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                List<String> targetList = isAllergies ? user.allergies : user.medicalConditions;
                targetList.remove(selected);
                updateDisplay(currentItems, user, isAllergies, mainDisplayArea);
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        contentPanel.add(new JScrollPane(currentItems), BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(contentPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setVisible(true);
    }

    private static void updateDisplay(JTextArea dialogArea, User user, boolean isAllergies, JTextArea mainDisplayArea) {
        if (isAllergies) {
            dialogArea.setText(String.join("\n", user.allergies));
            updateAllergiesDisplay(mainDisplayArea, user);
        } else {
            dialogArea.setText(String.join("\n", user.medicalConditions));
            updateConditionsDisplay(mainDisplayArea, user);
        }
        saveUserToFile(user);
    }

    private static JPanel createMedicalButtons(String type, User user, JTextArea displayArea) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton manageButton = new JButton("Manage " + type);
        manageButton.setFont(new Font("Arial", Font.BOLD, 14));

        JPopupMenu popupMenu = new JPopupMenu();
        String[] actions = { "Add", "Edit", "Remove" };

        for (String action : actions) {
            JMenuItem item = new JMenuItem(action);
            item.addActionListener(e -> handleMedicalAction(action, type, user, displayArea));
            popupMenu.add(item);
        }

        manageButton.addActionListener(e -> popupMenu.show(manageButton, 0, manageButton.getHeight()));
        panel.add(manageButton);

        return panel;
    }

    private static void handleMedicalAction(String action, String type, User user, JTextArea displayArea) {
        List<String> items = type.equals("Allergies") ? user.allergies : user.medicalConditions;

        switch (action) {
            case "Add" -> {
                JComboBox<String> combo = new JComboBox<>(
                        type.equals("Allergies") ? COMMON_ALLERGIES : COMMON_MEDICAL_CONDITIONS);
                combo.setEditable(true);

                JPanel panel = new JPanel();
                panel.add(new JLabel("Add " + type + ":"));
                panel.add(combo);

                if (type.equals("Allergies")) {
                    JComboBox<String> severity = new JComboBox<>(new String[] { "mild", "severe" });
                    panel.add(severity);
                }

                if (JOptionPane.showConfirmDialog(null, panel) == JOptionPane.OK_OPTION) {
                    String value = ((String) combo.getSelectedItem()).split(":")[1].trim();
                    if (type.equals("Allergies")) {
                        value += " (" + ((JComboBox<String>) panel.getComponent(2)).getSelectedItem() + ")";
                    }
                    items.add(value);
                    saveUserToFile(user);
                    if (type.equals("Allergies")) {
                        updateAllergiesDisplay(displayArea, user);
                    } else {
                        updateConditionsDisplay(displayArea, user);
                    }
                }
            }
            case "Edit" -> {
                if (items.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No " + type + " to edit");
                    return;
                }

                String selected = (String) JOptionPane.showInputDialog(
                        null,
                        "Select " + type + " to edit:",
                        "Edit " + type,
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        items.toArray(),
                        items.get(0));

                if (selected != null) {
                    int index = items.indexOf(selected);
                    JTextField field = new JTextField(selected.replaceAll("\\(.*\\)", "").trim(), 20);
                    JPanel panel = new JPanel();
                    panel.add(new JLabel("New value:"));
                    panel.add(field);

                    if (type.equals("Allergies")) {
                        JComboBox<String> severity = new JComboBox<>(new String[] { "mild", "severe" });
                        severity.setSelectedItem(selected.contains("severe") ? "severe" : "mild");
                        panel.add(severity);
                    }

                    if (JOptionPane.showConfirmDialog(null, panel) == JOptionPane.OK_OPTION) {
                        String newValue = field.getText();
                        if (type.equals("Allergies")) {
                            newValue += " (" + ((JComboBox<String>) panel.getComponent(2)).getSelectedItem() + ")";
                        }
                        items.set(index, newValue);
                        saveUserToFile(user);
                        if (type.equals("Allergies")) {
                            updateAllergiesDisplay(displayArea, user);
                        } else {
                            updateConditionsDisplay(displayArea, user);
                        }
                    }
                }
            }
            case "Remove" -> {
                if (items.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No " + type + " to remove");
                    return;
                }

                String selected = (String) JOptionPane.showInputDialog(
                        null,
                        "Select " + type + " to remove:",
                        "Remove " + type,
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        items.toArray(),
                        items.get(0));

                if (selected != null) {
                    items.remove(selected);
                    saveUserToFile(user);
                    if (type.equals("Allergies")) {
                        updateAllergiesDisplay(displayArea, user);
                    } else {
                        updateConditionsDisplay(displayArea, user);
                    }
                }
            }
        }
    }

    private static void updateAllergiesDisplay(JTextArea area, User user) {
        area.setText("");
        if (user.allergies.isEmpty()) {
            area.setText("No allergies recorded");
        } else {
            user.allergies.forEach(allergy -> area.append("• " + allergy + "\n"));
        }
    }

    private static void updateConditionsDisplay(JTextArea area, User user) {
        area.setText("");
        if (user.medicalConditions.isEmpty()) {
            area.setText("No medical conditions recorded");
        } else {
            user.medicalConditions.forEach(condition -> area.append("• " + condition + "\n"));
        }
    }

    private static JPanel createCategoryButtons(User user, JTextArea displayArea) {
        JPanel panel = new JPanel(new GridLayout(1, 2, 20, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Allergies Button
        JButton allergiesBtn = createCategoryButton("Allergies", user, displayArea);

        // Conditions Button
        JButton conditionsBtn = createCategoryButton("Medical Conditions", user, displayArea);

        panel.add(allergiesBtn);
        panel.add(conditionsBtn);

        return panel;
    }

    private static JButton createCategoryButton(String text, User user, JTextArea displayArea) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(150, 40)); // Larger buttons
        button.setFont(new Font("Arial", Font.BOLD, 14));

        JPopupMenu menu = new JPopupMenu();
        menu.setPreferredSize(new Dimension(150, 120));

        String[] actions = { "Add", "Edit", "Remove" };
        for (String action : actions) {
            JMenuItem item = new JMenuItem(action);
            item.addActionListener(e -> handleCategoryAction(user, text, action, displayArea));
            item.setFont(new Font("Arial", Font.PLAIN, 13));
            menu.add(item);
        }

        button.addActionListener(e -> menu.show(button, 0, button.getHeight()));
        return button;
    }

    private static void handleCategoryAction(User user, String categoryType, String action, JTextArea displayArea) {
        switch (action) {
            case "Add" -> showAddDialog(user, categoryType, displayArea);
            case "Edit" -> showEditDialog(user, categoryType, displayArea);
            case "Remove" -> showRemoveDialog(user, categoryType, displayArea);
        }
    }

    private static JPanel createAllergyConditionButtons(User user, JTextArea displayArea) {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));

        // Allergies Menu
        JButton allergiesBtn = new JButton("Allergies");
        JPopupMenu allergiesMenu = new JPopupMenu();
        addMenuItems(allergiesMenu, user, "Allergies", displayArea);
        allergiesBtn.addActionListener(e -> allergiesMenu.show(allergiesBtn, 0, allergiesBtn.getHeight()));

        // Medical Conditions Menu
        JButton conditionsBtn = new JButton("Medical Conditions");
        JPopupMenu conditionsMenu = new JPopupMenu();
        addMenuItems(conditionsMenu, user, "Medical Conditions", displayArea);
        conditionsBtn.addActionListener(e -> conditionsMenu.show(conditionsBtn, 0, conditionsBtn.getHeight()));

        panel.add(allergiesBtn);
        panel.add(conditionsBtn);

        return panel;
    }

    private static void addMenuItems(JPopupMenu menu, User user, String type, JTextArea displayArea) {
        JMenuItem addItem = new JMenuItem("Add");
        addItem.addActionListener(e -> showAddDialog(user, type, displayArea));

        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(e -> showEditDialog(user, type, displayArea));

        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> showRemoveDialog(user, type, displayArea));

        menu.add(addItem);
        menu.add(editItem);
        menu.add(removeItem);
    }

    private static void showAddDialog(User user, String type, JTextArea displayArea) {
        JComboBox<String> combo = new JComboBox<>(
                type.equals("Allergies") ? COMMON_ALLERGIES : COMMON_MEDICAL_CONDITIONS);
        combo.setEditable(true);
        JComboBox<String> severity = new JComboBox<>(new String[] { "mild", "severe" }); // Moved here

        JPanel panel = new JPanel();
        panel.add(new JLabel("Add " + type + ":"));
        panel.add(combo);

        if (type.equals("Allergies")) {
            panel.add(severity);
        }

        if (JOptionPane.showConfirmDialog(null, panel) == JOptionPane.OK_OPTION) {
            String value = ((String) combo.getSelectedItem()).split(":")[1].trim();
            if (type.equals("Allergies")) {
                value += " (" + severity.getSelectedItem() + ")";
            }
            (type.equals("Allergies") ? user.allergies : user.medicalConditions).add(value);
            saveUserToFile(user);
            updateDisplayArea(displayArea, user);
        }
    }

    private static void showEditDialog(User user, String type, JTextArea displayArea) {
        List<String> list = type.equals("Allergies") ? user.allergies : user.medicalConditions;
        if (list.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No " + type + " to edit");
            return;
        }

        String selected = (String) JOptionPane.showInputDialog(mainFrame,
                "Select " + type + " to edit:",
                "Edit " + type,
                JOptionPane.PLAIN_MESSAGE,
                null,
                list.toArray(),
                list.get(0));

        if (selected != null) {
            JPanel panel = new JPanel();
            JTextField field = new JTextField(selected.replaceAll("\\(.*\\)", "").trim(), 20);
            panel.add(new JLabel("New value:"));
            panel.add(field);

            // Declare severity outside the if-block
            JComboBox<String> severity = null;
            if (type.equals("Allergies")) {
                severity = new JComboBox<>(new String[] { "mild", "severe" });
                panel.add(severity);
            }

            if (JOptionPane.showConfirmDialog(mainFrame, panel) == JOptionPane.OK_OPTION) {
                int index = list.indexOf(selected);
                String newValue = field.getText();

                // Only add severity if it exists
                if (type.equals("Allergies") && severity != null) {
                    newValue += " (" + severity.getSelectedItem() + ")";
                }

                list.set(index, newValue);
                saveUserToFile(user);
                updateDisplayArea(displayArea, user);
            }
        }
    }

    private static void showRemoveDialog(User user, String type, JTextArea displayArea) {
        List<String> list = type.equals("Allergies") ? user.allergies : user.medicalConditions;
        if (list.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No " + type + " to remove");
            return;
        }

        String selected = (String) JOptionPane.showInputDialog(mainFrame,
                "Select " + type + " to remove:",
                "Remove " + type,
                JOptionPane.PLAIN_MESSAGE,
                null,
                list.toArray(),
                list.get(0));

        if (selected != null) {
            list.remove(selected);
            saveUserToFile(user);
            updateDisplayArea(displayArea, user);
        }
    }

    private static JTabbedPane createVitalsPanel(User user) {
        JTabbedPane tabbedPane = new JTabbedPane();
        // Pass the user to createInputVitalsPanel
        tabbedPane.addTab("Input Vitals", createInputVitalsPanel(user));
        tabbedPane.addTab("View Vitals", createViewVitalsPanel(user));
        return tabbedPane;
    }

    private static JPanel createInputVitalsPanel(User user) {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 5, 5));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
        JTextField dateField = new JTextField(LocalDateTime.now().format(dtf));
        dateField.setEditable(false);

        // Create array of components for easier access
        JTextField heightField = new JTextField();
        JTextField weightField = new JTextField();
        JTextField bpField = new JTextField();
        JTextField hrField = new JTextField();
        JTextField tempField = new JTextField();

        inputPanel.add(new JLabel("Date:"));
        inputPanel.add(dateField);
        inputPanel.add(new JLabel("Height (cm):"));
        inputPanel.add(heightField);
        inputPanel.add(new JLabel("Weight (kg):"));
        inputPanel.add(weightField);
        inputPanel.add(new JLabel("Blood Pressure:"));
        inputPanel.add(bpField);
        inputPanel.add(new JLabel("Heart Rate (bpm):"));
        inputPanel.add(hrField);
        inputPanel.add(new JLabel("Temperature (°C):"));
        inputPanel.add(tempField);

        JComboBox<String> concernCombo = new JComboBox<>(new String[] { "Routine Check", "Emergency", "Consultation" });
        inputPanel.add(new JLabel("Concern Type:"));
        inputPanel.add(concernCombo);

        JTextArea notesArea = new JTextArea(4, 20);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        inputPanel.add(new JLabel("Patient's Notes:"));
        inputPanel.add(notesScroll);

        // in private static JPanel createInputVitalsPanel(User user)

        JButton referButton = new JButton("Refer to Doctor");
        referButton.addActionListener(e -> {
            // --- MODIFICATION START ---
            // First, check if a user is logged in. This prevents the null pointer crash.
            if (currentUser == null) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Error: Could not identify the referring staff member. Please log out and try again.",
                        "Session Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // --- MODIFICATION END ---

            try {
                String record = String.join(";",
                        String.valueOf(user.id),
                        dateField.getText(),
                        heightField.getText(),
                        weightField.getText(),
                        bpField.getText(),
                        hrField.getText(),
                        tempField.getText(),
                        concernCombo.getSelectedItem().toString(),
                        notesArea.getText().replace("\n", "\\n"),
                        "REFERRED",
                        currentUser.username, // This line is now safe to execute
                        "Referred for consultation");

                if (heightField.getText().isEmpty() || weightField.getText().isEmpty() || bpField.getText().isEmpty()
                        || hrField.getText().isEmpty() || tempField.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(mainPanel, "Please fill all required fields!");
                    return;
                }

                Files.write(Paths.get(VITALS_DATABASE),
                        (record + System.lineSeparator()).getBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);

                JOptionPane.showMessageDialog(mainPanel, "Patient referred to doctor!");
            } catch (Exception ex) {
                ex.printStackTrace(); // Log the error
                JOptionPane.showMessageDialog(mainPanel, "Error: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(referButton, BorderLayout.SOUTH);

        return mainPanel;
    }

    private static JPanel createViewVitalsPanel(User user) {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea vitalsArea = new JTextArea();
        vitalsArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(vitalsArea);

        // Add a component listener to reload data when the panel becomes visible
        panel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadVitalsData(user.id, vitalsArea);
            }
        });

        // Initial load
        loadVitalsData(user.id, vitalsArea);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private static void loadVitalsData(int userId, JTextArea area) {
        area.setText("");
        try {
            Path path = Paths.get(VITALS_DATABASE);
            if (!Files.exists(path))
                return;

            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String[] parts = line.split(";", -1); // Split all parts
                if (parts.length > 9 && parts[9].equals("REFERRED")) {
                    area.append("Date: " + parts[1] + "\n" +
                            "Height: " + parts[2] + " cm\n" +
                            "Weight: " + parts[3] + " kg\n" +
                            "BP: " + parts[4] + "\n" +
                            "HR: " + parts[5] + " bpm\n" +
                            "Temp: " + parts[6] + " °C\n" +
                            "Concern: " + parts[7] + "\n" +
                            "Notes: " + parts[8].replace("\\n", "\n") + "\n" +
                            "Status: " + parts[9] + "\n" +
                            "Referred by: " + parts[10] + "\n\n");
                }
            }
        } catch (Exception ex) {
            area.setText("Error loading vitals: " + ex.getMessage());
        }
    }

    // DOCTORSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS

    private static void createDoctorMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Hi Doc, " + (currentUser != null ? currentUser.name : ""),
                SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(titleLabel, gbc);

        String[] options = {
                "1. Receive Patient",
                "2. Scan QR Code",
                "3. Manage Account",
                "4. Exit"
        };

        for (String option : options) {
            JButton button = new JButton(option);
            button.addActionListener(e -> handleDoctorMenuChoice(option));
            panel.add(button, gbc);
        }

        cardPanel.add(panel, "DoctorMenu");
    }

    private static void handleDoctorMenuChoice(String choice) {
        switch (choice.charAt(0)) {
            case '1' -> showReferredPatientsDialog();
            case '2' -> startScanner();
            case '3' -> showManageAccountDialog();
            case '4' -> {
                currentUser = null;
                // Use the correct variable names for your login fields
                loginUserField.setText("");
                loginPassField.setText("");
                cardLayout.show(cardPanel, "MainMenu");
            }
        }
    }

    private static List<User> loadDoctorsFromFile() {
        List<User> doctors = new ArrayList<>();
        try {
            Path path = Paths.get(DOCTORS_DATABASE);
            if (!Files.exists(path))
                return doctors;

            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                User doctor = User.fromString(line);
                if (doctor != null)
                    doctors.add(doctor);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error loading doctor data", "Error", JOptionPane.ERROR_MESSAGE);
        }
        return doctors;
    }

    private static void showReferredPatientsDialog() {
        try {
            Path vitalsPath = Paths.get(VITALS_DATABASE);
            if (!Files.exists(vitalsPath)) {
                JOptionPane.showMessageDialog(mainFrame, "No referral data found.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            List<String> referrals = Files.readAllLines(vitalsPath).stream()
                    .filter(line -> {
                        String[] parts = line.split(";", -1);
                        return parts.length > 9 && "REFERRED".equals(parts[9].trim());
                    })
                    .collect(Collectors.toList());

            if (referrals.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No pending patients.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] referralDisplay = referrals.stream()
                    .map(line -> {
                        String[] parts = line.split(";", -1);
                        User patient = users.stream()
                                .filter(u -> String.valueOf(u.id).equals(parts[0].trim()))
                                .findFirst()
                                .orElse(null);
                        return patient != null ? patient.name + " - Referred at: " + parts[1]
                                : "Unknown Patient - " + parts[1];
                    })
                    .toArray(String[]::new);

            String selectedReferral = (String) JOptionPane.showInputDialog(
                    mainFrame,
                    "Select a patient to receive:",
                    "Referred Patients",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    referralDisplay,
                    referralDisplay[0]);

            if (selectedReferral != null) {
                int selectedIndex = Arrays.asList(referralDisplay).indexOf(selectedReferral);
                String[] parts = referrals.get(selectedIndex).split(";", -1);
                User selectedPatient = users.stream()
                        .filter(u -> String.valueOf(u.id).equals(parts[0].trim()))
                        .findFirst()
                        .orElse(null);

                if (selectedPatient != null) {
                    // Create dialog with all patient information tabs
                    JDialog patientDialog = new JDialog(mainFrame, "Patient Profile - " + selectedPatient.name, true);
                    patientDialog.setSize(900, 600);
                    patientDialog.setLocationRelativeTo(mainFrame);

                    JTabbedPane tabbedPane = new JTabbedPane();

                    // Tab 1: Profile
                    tabbedPane.addTab("Profile", createProfileTab(selectedPatient));

                    // Tab 2: Medical History
                    JPanel historyPanel = new JPanel(new BorderLayout());
                    JTextArea historyText = new JTextArea(selectedPatient.getMedicalHistoryAsString());
                    historyText.setEditable(false);
                    historyPanel.add(new JScrollPane(historyText), BorderLayout.CENTER);
                    tabbedPane.addTab("Medical History", historyPanel);

                    // Tab 3: Current Medications
                    JTextArea currentMedsArea = new JTextArea();
                    currentMedsArea.setEditable(false);
                    List<String[]> currentMeds = loadMedicationsForUser(selectedPatient.username, false);
                    if (currentMeds.isEmpty()) {
                        currentMedsArea.setText("No current medications.");
                    } else {
                        for (String[] med : currentMeds) {
                            currentMedsArea.append("Medication: " + med[1] + "\n");
                            currentMedsArea.append("Dosage: " + med[2] + "\n");
                            currentMedsArea.append("Instructions: " + med[3] + "\n");
                            currentMedsArea.append("Period: " + med[4] + " to " + med[5] + "\n");
                            currentMedsArea.append("--------\n");
                        }
                    }
                    tabbedPane.addTab("Current Medications", new JScrollPane(currentMedsArea));

                    // Tab 4: Medication History
                    JTextArea pastMedsArea = new JTextArea();
                    pastMedsArea.setEditable(false);
                    List<String[]> pastMeds = loadMedicationsForUser(selectedPatient.username, true);
                    if (pastMeds.isEmpty()) {
                        pastMedsArea.setText("No past medications.");
                    } else {
                        for (String[] med : pastMeds) {
                            pastMedsArea.append("Medication: " + med[1] + "\n");
                            pastMedsArea.append("Dosage: " + med[2] + "\n");
                            pastMedsArea.append("Instructions: " + med[3] + "\n");
                            pastMedsArea.append("Period: " + med[4] + " to " + med[5] + "\n");
                            pastMedsArea.append("--------\n");
                        }
                    }
                    tabbedPane.addTab("Medication History", new JScrollPane(pastMedsArea));

                    // Tab 5: Vitals Assessment (from referral)
                    JPanel vitalsPanel = createDoctorViewVitalsPanel(referrals.get(selectedIndex));
                    tabbedPane.addTab("Vitals Assessment", vitalsPanel);

                    // Tab 6: Add Medication
                    tabbedPane.addTab("Add Medication", createDoctorMedicationPanel(selectedPatient));

                    // Tab 7: Add Medical History
                    tabbedPane.addTab("Add Medical History", createDoctorHistoryPanel(selectedPatient));

                    patientDialog.add(tabbedPane);
                    patientDialog.setVisible(true);

                    // Update the referral status to "RECEIVED"
                    updateReferralStatus(selectedPatient.id, parts[1], "RECEIVED");
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(mainFrame, "Error accessing patient data: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void updateReferralStatus(int patientId, String referralDate, String newStatus) {
        try {
            Path vitalsPath = Paths.get(VITALS_DATABASE);
            List<String> lines = Files.readAllLines(vitalsPath);
            List<String> updatedLines = new ArrayList<>();

            for (String line : lines) {
                String[] parts = line.split(";", -1);
                if (parts.length > 9 && String.valueOf(patientId).equals(parts[0].trim())
                        && referralDate.equals(parts[1].trim())) {
                    // Update the status
                    parts[9] = newStatus;
                    // Update the doctor who received the patient
                    parts[10] = currentUser.username;
                    line = String.join(";", parts);
                }
                updatedLines.add(line);
            }

            Files.write(vitalsPath, updatedLines);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(mainFrame, "Error updating referral status: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void showPatientDetails() {
        if (currentPatient == null)
            return;
        // Implement your patient display logic here
        JOptionPane.showMessageDialog(null, "Now viewing: " + currentPatient.name, "Patient Received",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showPatientTabs(User patient, String referralData) {
        JDialog patientDialog = new JDialog(mainFrame, "Patient Management - " + patient.name, true);
        patientDialog.setSize(800, 600);
        patientDialog.setLocationRelativeTo(mainFrame);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Vital Assessment Tab
        tabbedPane.addTab("Vital Assessment", createDoctorViewVitalsPanel(referralData));

        // Add Medication Tab
        tabbedPane.addTab("Add Medication", createDoctorMedicationPanel(patient));

        // Add Medical History Tab
        tabbedPane.addTab("Add Medical History", createDoctorHistoryPanel(patient));

        patientDialog.add(tabbedPane);
        patientDialog.setVisible(true);
    }

    private static JPanel createMedicationTab(User patient) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Patient Info Panel at the top
        JPanel patientInfoPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        patientInfoPanel.setBorder(BorderFactory.createTitledBorder("Patient Information"));
        addInfoField(patientInfoPanel, "Name:", patient.name);
        addInfoField(patientInfoPanel, "Age:", String.valueOf(patient.age));
        addInfoField(patientInfoPanel, "Blood Type:", patient.bloodType);
        addInfoField(patientInfoPanel, "Allergies:", String.join(", ", patient.allergies));
        panel.add(patientInfoPanel, BorderLayout.NORTH);

        // Medication Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JTextField nameField = new JTextField(20);
        JTextField dosageField = new JTextField(10);
        JTextArea instructionsArea = new JTextArea(3, 20);
        JTextField startDateField = new JTextField(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")));
        JTextField endDateField = new JTextField();

        int row = 0;
        addFormField(formPanel, gbc, "Medication Name:", nameField, row++);
        addFormField(formPanel, gbc, "Dosage Amount:", dosageField, row++);
        addFormField(formPanel, gbc, "Special Instructions:", new JScrollPane(instructionsArea), row++);
        addFormField(formPanel, gbc, "Starting Date & Time (MM/dd/yyyy HH:mm):", startDateField, row++);
        addFormField(formPanel, gbc, "Ending Date & Time (MM/dd/yyyy HH:mm):", endDateField, row++);

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            if (validateMedicationFields(nameField, dosageField, startDateField, endDateField)) {
                // Get the current doctor's name
                String doctorName = (currentUser != null) ? currentUser.name : "Unknown Doctor";

                // Call saveMedicationToFile with the doctor's name
                saveMedicationToFile(patient.username,
                        nameField.getText(),
                        dosageField.getText(),
                        instructionsArea.getText(),
                        startDateField.getText(),
                        endDateField.getText(),
                        doctorName); // Added doctorName argument
                JOptionPane.showMessageDialog(panel, "Medication added successfully!");
            }
        });
        cancelButton.addActionListener(e -> ((Window) SwingUtilities.getRoot(panel)).dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        formPanel.add(buttonPanel, gbc);

        panel.add(new JScrollPane(formPanel), BorderLayout.CENTER);

        return panel;
    }

    private static void addInfoField(JPanel panel, String label, String value) {
        panel.add(new JLabel(label));
        panel.add(new JLabel(value.isEmpty() ? "N/A" : value));
    }

    private static JPanel createHistoryTab(User patient) {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea historyInput = new JTextArea(5, 20);
        JButton saveHistoryBtn = new JButton("Save History");

        saveHistoryBtn.addActionListener(e -> {
            if (patient != null && !historyInput.getText().isEmpty()) {
                patient.medicalHistory.add(new String[] {
                        historyInput.getText(),
                        new SimpleDateFormat("MM/dd/yyyy").format(new Date())
                });
                updateDatabaseFile();
                JOptionPane.showMessageDialog(panel, "Medical history added successfully!");
                historyInput.setText("");
            } else {
                JOptionPane.showMessageDialog(panel, "No patient selected or history text empty!");
            }
        });

        panel.add(new JScrollPane(historyInput), BorderLayout.CENTER);
        panel.add(saveHistoryBtn, BorderLayout.SOUTH);
        return panel;
    }

    private static void saveMedication(User patient, String name, String dosage,
            String instructions, String endDate) {
        String medData = String.join(";",
                patient.username,
                name,
                dosage,
                instructions,
                new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date()),
                endDate);

        try {
            Files.write(Paths.get(MEDICATION_DATABASE),
                    (medData + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Error saving medication!");
        }
    }

    private static void saveMedicalHistory(String historyText) {
        if (currentPatient == null) {
            JOptionPane.showMessageDialog(null, "No patient selected!");
            return;
        }

        if (!historyText.isEmpty()) {
            currentPatient.medicalHistory.add(new String[] {
                    historyText,
                    new SimpleDateFormat("MM/dd/yyyy").format(new Date())
            });
            updateDatabaseFile();
            JOptionPane.showMessageDialog(null, "History saved!");
        }
    }

    private static void showCurrentMedications(User patient) {
        JTextArea medArea = new JTextArea();
        List<String[]> meds = loadMedicationsForUser(patient.username, false);
        // Populate medArea with medication details...
        JOptionPane.showMessageDialog(mainFrame, new JScrollPane(medArea));
    }

}