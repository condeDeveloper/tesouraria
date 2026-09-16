package br.com.conde.tesouraria.contrapartes.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoTest {

    @Test
    void aceitaCpfValidoComOuSemMascara() {
        assertThat(Documento.normalizar("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Documento.normalizar("52998224725")).isEqualTo("52998224725");
        assertThat(Documento.tipoPara("52998224725")).isEqualTo(TipoContraparte.PF);
    }

    @Test
    void aceitaCnpjValido() {
        assertThat(Documento.normalizar("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(Documento.tipoPara("11222333000181")).isEqualTo(TipoContraparte.PJ);
    }

    @Test
    void recusaDigitosVerificadoresErrados() {
        assertThatThrownBy(() -> Documento.normalizar("529.982.247-26")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Documento.normalizar("11.222.333/0001-82")).isInstanceOf(DomainException.class);
    }

    @Test
    void recusaSequenciasRepetidasETamanhoErrado() {
        assertThatThrownBy(() -> Documento.normalizar("111.111.111-11")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Documento.normalizar("00.000.000/0000-00")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Documento.normalizar("12345")).isInstanceOf(DomainException.class);
    }

    @Test
    void formataParaExibicao() {
        assertThat(Documento.formatar("52998224725")).isEqualTo("529.982.247-25");
        assertThat(Documento.formatar("11222333000181")).isEqualTo("11.222.333/0001-81");
    }
}
