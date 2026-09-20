package nl.logicai.wiki.services;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The upload type comes from the bytes, not from the browser's content type or the file name (spec U-01). */
class AttachmentServiceTest {

	@Test
	void recognisesPngByItsSignature() {
		byte[] head = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
		assertThat(AttachmentService.detectContentType(head)).isEqualTo("image/png");
	}

	@Test
	void recognisesJpegByItsSignature() {
		byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};
		assertThat(AttachmentService.detectContentType(head)).isEqualTo("image/jpeg");
	}

	@Test
	void recognisesPdfByItsHeader() {
		assertThat(AttachmentService.detectContentType("%PDF-1.7\n%âã".getBytes(StandardCharsets.ISO_8859_1)))
			.isEqualTo("application/pdf");
	}

	@Test
	void recognisesMp4ByTheFtypBox() {
		byte[] head = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
		assertThat(AttachmentService.detectContentType(head)).isEqualTo("video/mp4");
	}

	@Test
	void recognisesWebmByTheEbmlHeader() {
		byte[] head = {(byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0, 0, 0, 0, 0, 0, 0x1F, 0x42, (byte) 0x86, (byte) 0x81, 0x01};
		assertThat(AttachmentService.detectContentType(head)).isEqualTo("video/webm");
	}

	@Test
	void refusesActiveAndUnknownFormats() {
		assertThat(AttachmentService.detectContentType("<html><script>".getBytes(StandardCharsets.US_ASCII))).isNull();
		assertThat(AttachmentService.detectContentType("<svg xmlns=\"http".getBytes(StandardCharsets.US_ASCII))).isNull();
		assertThat(AttachmentService.detectContentType("GIF89a".getBytes(StandardCharsets.US_ASCII))).isNull();
		assertThat(AttachmentService.detectContentType("%PDF".getBytes(StandardCharsets.US_ASCII))).isNull();
		assertThat(AttachmentService.detectContentType(new byte[] {(byte) 0x89, 'P', 'N'})).isNull();
		assertThat(AttachmentService.detectContentType(new byte[0])).isNull();
		assertThat(AttachmentService.detectContentType(new byte[] {0, 0, 0})).isNull();
	}

	@Test
	void videoHasItsOwnSizeLimit() {
		assertThat(AttachmentService.isVideo("video/mp4")).isTrue();
		assertThat(AttachmentService.isVideo("video/webm")).isTrue();
		assertThat(AttachmentService.isVideo("image/png")).isFalse();
		assertThat(AttachmentService.isVideo("application/pdf")).isFalse();
	}

	@Test
	void onlyInternalAttachmentUrlsAreAccepted() {
		assertThat(AttachmentService.isInternalUrl("/attachments/11111111-1111-4111-8111-111111111101")).isTrue();
		assertThat(AttachmentService.isInternalUrl("/attachments/11111111-1111-4111-8111-111111111101/../x")).isFalse();
		assertThat(AttachmentService.isInternalUrl("https://evil.example/video.mp4")).isFalse();
		assertThat(AttachmentService.isInternalUrl("/attachments/not-a-uuid")).isFalse();
		assertThat(AttachmentService.isInternalUrl("")).isFalse();
		assertThat(AttachmentService.isInternalUrl(null)).isFalse();
	}

}
