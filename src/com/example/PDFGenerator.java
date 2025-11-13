package com.example;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
// import com.example.QRScanner.User; // Already have this if User is accessible
// import com.itextpdf.text.*; // Duplicate
// import com.itextpdf.text.pdf.*; // Duplicate
// import java.io.File; // Unused
// import java.io.FileOutputStream; // Duplicate
// import java.time.LocalDate; // Unused in this file context
// import java.util.List; // Duplicate
// import javax.swing.JFileChooser; // Unused
// import javax.swing.JFrame; // Unused
import javax.swing.JOptionPane;

public class PDFGenerator {

    public static void generateUserPDF(QRScanner.User user, List<String[]> currentMeds, List<String[]> pastMeds) {
        Document document = new Document();
        try {
            // Create PDF file with username as filename
            String filePath = "C:\\Users\\labor\\OneDrive\\Desktop\\scanner\\" + user.username + "_profile.pdf";
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Add creation date in header
            addCreationDateHeader(writer, document);

            // Add title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("DHR Profile", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Add user image if available
            if (user.imagePath != null && !user.imagePath.isEmpty()) {
                try {
                    Image image = Image.getInstance(user.imagePath);
                    image.scaleToFit(150, 150);
                    image.setAlignment(Element.ALIGN_CENTER);
                    document.add(image);
                    document.add(new Paragraph(" ")); // Add space
                } catch (Exception e) {
                    System.err.println("Could not load user image: " + e.getMessage());
                }
            }

            // Create user info table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // Set column widths
            float[] columnWidths = { 1f, 2f };
            table.setWidths(columnWidths);

            // Add user data
            addTableRow(table, "Name:", user.name);
            addTableRow(table, "Age:", String.valueOf(user.age));
            addTableRow(table, "Birthday:", user.birthday);
            addTableRow(table, "Civil Status:", user.civilStatus);
            addTableRow(table, "Blood Type:", user.bloodType);
            addTableRow(table, "Contact Number:", user.contactNumber);
            addTableRow(table, "Emergency Contact:", user.emergencyContactNumber);

            document.add(table);

            // Add medical information sections
            addSection(document, "Allergies", user.allergies);
            addSection(document, "Medical Conditions", user.medicalConditions);

            // Add medical history
            if (!user.medicalHistory.isEmpty()) {
                Paragraph historyTitle = new Paragraph("Medical History",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK));
                historyTitle.setSpacingBefore(15);
                historyTitle.setSpacingAfter(10);
                document.add(historyTitle);

                for (String[] history : user.medicalHistory) {
                    Paragraph historyItem = new Paragraph();
                    historyItem.add(new Chunk("• " + history[0] + "\n", // History text
                            FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK)));
                    historyItem.add(new Chunk("  Date: " + history[1] + "\n", // Date
                            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, BaseColor.DARK_GRAY)));

                    // --- MODIFICATION START: Add Doctor's Name to Medical History ---
                    if (history.length > 3 && history[3] != null && !history[3].isEmpty()) {
                        historyItem.add(new Chunk("  Recorded By: Dr. " + history[3] + "\n", // Doctor's Name
                                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, BaseColor.DARK_GRAY)));
                    }
                    // --- MODIFICATION END ---
                    historyItem.add(new Chunk("\n",
                            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, BaseColor.DARK_GRAY))); // Spacing
                                                                                                           // after
                                                                                                           // entry
                    document.add(historyItem);
                }
            }

            // Add current medications
            addMedicationSection(document, "Current Medications", currentMeds);

            // Add medication history
            addMedicationSection(document, "Medication History", pastMeds);

            document.close();

            // Show success message in the main application thread
            javax.swing.SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "PDF generated successfully!\nSaved to: " + filePath,
                        "PDF Generated",
                        JOptionPane.INFORMATION_MESSAGE);
            });
        } catch (Exception e) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "Error generating PDF: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            });
            e.printStackTrace(); // For console debugging
        }
    }

    private static void addCreationDateHeader(PdfWriter writer, Document document) throws DocumentException {
        // Create a header table with the current date
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setHorizontalAlignment(Element.ALIGN_RIGHT);

        // Format the current date
        String currentDate = new SimpleDateFormat("MMM dd, yyyy HH:mm").format(new Date());
        Phrase datePhrase = new Phrase("Generated on: " + currentDate,
                FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY));

        PdfPCell cell = new PdfPCell(datePhrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPaddingBottom(10);
        header.addCell(cell);

        // Add the header to the document
        document.add(header);
    }

    private static void addMedicationSection(Document document, String title, List<String[]> medications)
            throws DocumentException {
        if (!medications.isEmpty()) {
            Paragraph sectionTitle = new Paragraph(title,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK));
            sectionTitle.setSpacingBefore(15);
            sectionTitle.setSpacingAfter(10);
            document.add(sectionTitle);

            // --- MODIFICATION START: Add "Prescribed By" column ---
            // Create a table for medications - now with 5 columns
            PdfPTable medTable = new PdfPTable(5);
            medTable.setWidthPercentage(100);
            // Adjust column widths for 5 columns
            medTable.setWidths(new float[] { 2.5f, 1.5f, 3f, 2f, 2f });
            // --- MODIFICATION END ---
            medTable.setSpacingBefore(10f);
            medTable.setSpacingAfter(10f);

            // Add table headers
            addMedTableHeader(medTable, "Medication");
            addMedTableHeader(medTable, "Dosage");
            addMedTableHeader(medTable, "Instructions");
            addMedTableHeader(medTable, "Period");
            // --- MODIFICATION START: Add "Prescribed By" header ---
            addMedTableHeader(medTable, "Prescribed By");
            // --- MODIFICATION END ---

            // Add medication data
            for (String[] med : medications) {
                addMedTableRow(medTable, med[1]); // Medication name
                addMedTableRow(medTable, med[2]); // Dosage
                addMedTableRow(medTable, med[3]); // Instructions
                addMedTableRow(medTable, med[4] + " to " + med[5]); // Period
                // --- MODIFICATION START: Add Doctor's Name to Medication Table ---
                // Assuming med[6] will be the doctor's name from QRScanner.java
                if (med.length > 6 && med[6] != null && !med[6].isEmpty()) {
                    addMedTableRow(medTable, "Dr. " + med[6]);
                } else {
                    addMedTableRow(medTable, "N/A"); // Fallback if doctor info isn't there
                }
                // --- MODIFICATION END ---
            }

            document.add(medTable);
        }
    }

    private static void addMedTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE)));
        cell.setBackgroundColor(new BaseColor(70, 130, 180)); // Steel blue color
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static void addMedTableRow(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK)));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static void addTableRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static void addSection(Document document, String title, List<String> items) throws DocumentException {
        if (!items.isEmpty()) {
            Paragraph sectionTitle = new Paragraph(title,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK));
            sectionTitle.setSpacingBefore(15);
            sectionTitle.setSpacingAfter(10);
            document.add(sectionTitle);

            for (String item : items) {
                Paragraph itemPara = new Paragraph("• " + item,
                        FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK));
                document.add(itemPara);
            }
        }
    }
}